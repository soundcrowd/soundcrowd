/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.plugins

import android.content.Context
import android.graphics.Bitmap
import com.tiefensuche.soundcrowd.utils.LogHelper
import java.util.*

class PluginManager(private val context: Context) {

    val plugins = ArrayList<IPlugin>()
    var icons: MutableMap<String, Bitmap> = HashMap()

    fun init() {

        // Load all plugin classes from different packages that have the package prefix
        val pluginPackages = PackageUtil.getAppsByPrefix(context, PLUGIN_PACKAGE)
        for (pluginPackage in pluginPackages) {
            val plugin = PluginLoader.loadPlugin(context, pluginPackage)
            if (plugin != null) {
                plugin.init(context)
                plugins.add(plugin)
                // TODO Load plugin resources to be shown in the UI
                //                Context pluginContext = PackageUtil.getPackageContext(context, pluginPackage);
                //                Bitmap bitmap = BitmapFactory.decodeResource(pluginContext.getResources(), pluginContext.getResources().getIdentifier("plugin_icon", "drawable", pluginPackage));
                //                icons.put(plugin.name(), bitmap);
            }
        }

        // Load all plugins in classes that are in this context already
        // TODO merge with other plugin loader
        try {
            val cls = Class.forName("$PLUGIN_PACKAGE.cache.Plugin") as Class<IPlugin>
            val instance = cls.newInstance()
            instance.init(context)
//            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.plugin_icon)
//            icons[instance.name()] = bitmap
            plugins.add(instance)
        } catch (e: Exception) {
            LogHelper.e(javaClass.simpleName, e, "error loading plugin")
        }

    }

    companion object {

        private const val PLUGIN_PACKAGE = "com.tiefensuche.soundcrowd.plugins"
    }
}
