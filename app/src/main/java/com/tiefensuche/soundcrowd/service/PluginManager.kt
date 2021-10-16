/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.service

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.tiefensuche.soundcrowd.plugins.IPlugin
import dalvik.system.PathClassLoader
import java.util.*

internal class PluginManager(private val context: Context) {

    companion object {
        private const val PLUGIN_PACKAGE_PREFIX = "com.tiefensuche.soundcrowd.plugins"
        val plugins = HashMap<String, IPlugin>()

        fun handleCallback(url: Uri): Boolean {
            for (plugin in plugins.values) {
                for (clb in plugin.callbacks()) {
                    if (clb.key == url.host) {
                        clb.value(url.query)
                        return true
                    }
                }
            }
            return false
        }

        fun loadPlugin(context: Context, packageName: String): IPlugin? {
            return loadPlugin(context, packageName, "Plugin")
        }

        fun <T> loadPlugin(context: Context, packageName: String, className: String): T? {
            var plugin: T? = null
            try {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                val pcl = PathClassLoader(appInfo.sourceDir, context.classLoader)
                val pluginClass = pcl.loadClass("$packageName.$className")
                val pluginContext = getPackageContext(context, packageName)
                plugin = pluginClass.getConstructor(Context::class.java, Context::class.java).newInstance(context, pluginContext) as T
            } catch (e: java.lang.Exception) {
                Log.e(PluginManager::class.java.simpleName, "error loading plugin", e)
            }
            return plugin
        }

        private fun getPackageContext(context: Context, packageName: String): Context? {
            return try {
                context.applicationContext.createPackageContext(packageName,
                    Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

        private fun getAppsByPrefix(context: Context, prefix: String): List<String> {
            val packages = ArrayList<String>()
            for (applicationInfo in context.packageManager.getInstalledApplications(0)) {
                if (applicationInfo.packageName.startsWith(prefix)) {
                    packages.add(applicationInfo.packageName)
                }
            }
            return packages
        }
    }

    var initialized = false

    internal fun init() {
        // Load all plugin classes from different packages that have the package prefix
        val pluginPackages = getAppsByPrefix(context, PLUGIN_PACKAGE_PREFIX)
        for (pluginPackage in pluginPackages) {
            loadPlugin(context, pluginPackage)?.let { plugin ->
                if (!plugins.containsKey(plugin.name()))
                    plugins[plugin.name()] = plugin
            }
        }
        initialized = true
    }
}