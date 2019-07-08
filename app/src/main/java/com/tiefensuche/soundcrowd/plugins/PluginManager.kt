/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.plugins

import android.content.Context
import java.util.*

internal class PluginManager(private val context: Context) {

    companion object {
        private const val PLUGIN_PACKAGE_PREFIX = "com.tiefensuche.soundcrowd.plugins"
    }

    val plugins = ArrayList<IPlugin>()

    internal fun init() {

        // Load all plugin classes from different packages that have the package prefix
        val pluginPackages = PackageUtil.getAppsByPrefix(context, PLUGIN_PACKAGE_PREFIX)
        for (pluginPackage in pluginPackages) {
            PluginLoader.loadPlugin(context, pluginPackage)?.let { plugin ->
                plugins.add(plugin)
            }
        }
    }

    fun getPlugin(name: String): IPlugin? {
        for (plugin in plugins) {
            if (name == plugin.name()) {
                return plugin
            }
        }
        return null
    }
}