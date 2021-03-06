/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.plugins

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Proxy

internal object PluginLoader {

    internal fun loadPlugin(context: Context, packageName: String): IPlugin? {
        return loadPlugin<IPlugin>(context, packageName, "Plugin")
    }

    internal fun <T> loadPlugin(context: Context, packageName: String, className: String): T? {
        try {
            // Get the package context
            val pluginContext = PackageUtil.getPackageContext(context, packageName) ?: return null
            // Load the plugin class in the package
            val pluginClass = pluginContext.classLoader.loadClass("$packageName.$className")
            // Instantiate the plugin from the class
            val instance = pluginClass.getConstructor(Context::class.java, Context::class.java).newInstance(context, pluginContext)

            // Load the shared callback class from the plugin package with the foreign context
            val callbackClass = pluginContext.classLoader.loadClass("com.tiefensuche.soundcrowd.plugins.Callback")

            // Since the class loaders from this context and the foreign context are different
            // it is not possible to cast instantiated plugin class to the interface.
            // In the following a proxy instance is created from this context
            return Proxy.newProxyInstance(PluginLoader::class.java.classLoader, arrayOf<Class<*>>(IPlugin::class.java)) { _, method, objects ->
                // Get the parameter types from the invoked method and swap the callback classes
                val parameterTypes = arrayOfNulls<Class<*>>(method.parameterTypes.size)
                for (i in parameterTypes.indices) {
                    if (method.parameterTypes[i].name == "com.tiefensuche.soundcrowd.plugins.Callback") {
                        parameterTypes[i] = callbackClass
                    } else {
                        parameterTypes[i] = method.parameterTypes[i]
                    }
                }

                // Get the method from the foreign class
                val m = pluginClass.getMethod(method.name, *parameterTypes)

                var clb: Callback<Any>?

                // Go through all the passed objects and search for the callback class,
                // it is in the interface currently the only special object.
                if (objects != null) {
                    for (i in objects.indices) {
                        // search in the objects for the callback class
                        if (objects[i] is Callback<*>) {
                            clb = objects[i] as Callback<Any>
                            // Proxy the other way around, now from the foreign context and with the
                            // previously loaded foreign callback class and replace it with the proxy
                            objects[i] = Proxy.newProxyInstance(pluginContext.classLoader, arrayOf(callbackClass)) { _, _, objects ->
                                // simply invoke in sole callback method
                                objects?.get(0).let {
                                    // Could not find a better working solution yet
                                    // to support arbitrary types
                                    when (it) {
                                        is String -> (clb as Callback<String>).onResult(it)
                                        is JSONObject -> (clb as Callback<JSONObject>).onResult(it)
                                        is JSONArray -> (clb as Callback<JSONArray>).onResult(it)
                                        is Boolean -> (clb as Callback<Boolean>).onResult(it)
                                        else -> throw RuntimeException("unknown callback type")
                                    }
                                }
                            }
                        }
                    }
                }

                // Invoke the foreign method with the parameters and return the result
                if (objects == null) {
                    m.invoke(instance)
                } else {
                    m.invoke(instance, *objects)
                }

            } as? T
        } catch (e: Exception) {
            Log.e(PluginLoader::class.java.simpleName, "error loading plugin", e)
        }

        return null
    }
}