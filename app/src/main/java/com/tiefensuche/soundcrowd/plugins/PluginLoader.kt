/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.plugins



import android.content.Context
import com.tiefensuche.soundcrowd.utils.LogHelper
import org.json.JSONArray
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal object PluginLoader {

    fun loadPlugin(context: Context, classpath: String): IPlugin? {
        try {
            // Get the package context
            val pluginContext = PackageUtil.getPackageContext(context, classpath) ?: return null
            // Load the plugin class in the package
            val pluginClass = pluginContext.classLoader.loadClass("$classpath.Plugin")
            // Instantiate the plugin from the class
            val instance = pluginClass.newInstance()

            // Load the shared callback class from the plugin package with the foreign context
            val callbackClass = pluginContext.classLoader.loadClass("com.tiefensuche.soundcrowd.plugins.Callback")

            // Since the class loaders from this context and the foreign context are different
            // it is not possible to cast instantiated plugin class to the interface.
            // In the following a proxy instance is created from this context
            return Proxy.newProxyInstance(PluginLoader::class.java.classLoader, arrayOf<Class<*>>(IPlugin::class.java)) { _, method, objects ->
                // Get the parameter types from the invoked method and swap the callback classes
                val parameterTypes = arrayOfNulls<Class<*>>(method.parameterTypes.size)
                for (i in 0 until parameterTypes.size) {
                    if (method.parameterTypes[i].name == "com.tiefensuche.soundcrowd.plugins.Callback") {
                        parameterTypes[i] = callbackClass
                    } else {
                        parameterTypes[i] = method.parameterTypes[i]
                    }
                }

                // Get the method from the foreign class
                val m = pluginClass.getMethod(method.name, *parameterTypes)

                // Go through all the passed objects and search for the callback class,
                // it is in the interface currently the only special object.
                if (objects != null) {
                    for (i in objects.indices) {
                        // search in the objects for the callback class
                        val clb = objects[i]
                        if (clb is Callback<*>) {
                            // Proxy the other way around, now from the foreign context and with the
                            // previously loaded foreign callback class and replace it with the proxy
                            objects[i] = Proxy.newProxyInstance(pluginContext.classLoader, arrayOf(callbackClass), object: InvocationHandler {
                                override fun invoke(o: Any?, method: Method?, objects: Array<out Any>?) {
                                    // simply invoke in sole callback method
                                    objects?.get(0).let {
                                        if (it is JSONArray) {
                                            (clb as Callback<JSONArray>).onResult(it)
                                        } else if (it is String) {
                                            (clb as Callback<String>).onResult(it)
                                        }
                                    }
                                }
                            })
                        }
                    }
                }

                // Invoke the foreign method with the parameters and return the result
                if (objects == null) {
                    m.invoke(instance)
                } else {
                    m.invoke(instance, *objects)
                }
            } as? IPlugin
        } catch (e: Exception) {
            LogHelper.e(PluginLoader::class.java.simpleName, e, "error loading plugin")
        }

        return null
    }
}
