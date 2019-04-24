/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.plugins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.*

internal class PluginManager(private val context: Context) {

    val plugins = ArrayList<IPlugin>()
    var icons: MutableMap<String, Bitmap> = HashMap()

    internal fun init() {

        // Load all plugin classes from different packages that have the package prefix
        val pluginPackages = PackageUtil.getAppsByPrefix(context, PLUGIN_PACKAGE_PREFIX)
        for (pluginPackage in pluginPackages) {
            PluginLoader.loadPlugin(context, pluginPackage)?.let { plugin ->
                plugins.add(plugin)
                PackageUtil.getPackageContext(context, pluginPackage)?.let {
                    icons[plugin.name()] = BitmapFactory.decodeResource(it.resources, it.resources.getIdentifier("plugin_icon", "drawable", pluginPackage))
                }
            }
        }
    }

    companion object {

        private const val PLUGIN_PACKAGE_PREFIX = "com.tiefensuche.soundcrowd.plugins"
    }
}
