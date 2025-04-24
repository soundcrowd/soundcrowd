/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.service

import android.content.Context
import android.net.Uri
import com.tiefensuche.soundcrowd.plugins.IPlugin

internal class PluginManager(private val context: Context) {

    companion object {
        val plugins = HashMap<String, IPlugin>()

        fun handleCallback(url: Uri): Boolean {
            for (plugin in plugins.values) {
                for (clb in plugin.callbacks()) {
                    if (clb.key == url.host) {
                        clb.value(url.query!!)
                        return true
                    }
                }
            }
            return false
        }
    }

    var initialized = false

    internal fun init() {
        for (plugin in listOf(
            com.tiefensuche.soundcrowd.plugins.beatport.Plugin(context),
            com.tiefensuche.soundcrowd.plugins.soundcloud.Plugin(context),
            com.tiefensuche.soundcrowd.plugins.spotify.Plugin(context),
            com.tiefensuche.soundcrowd.plugins.tidal.Plugin(context),
            com.tiefensuche.soundcrowd.plugins.youtube.Plugin(context)
        )) {
            plugins[plugin.name()] = plugin
        }
        initialized = true
    }
}