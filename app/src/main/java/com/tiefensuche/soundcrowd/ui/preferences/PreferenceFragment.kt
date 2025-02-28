/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui.preferences

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.service.PluginManager
import com.tiefensuche.soundcrowd.ui.browser.MediaBrowserFragment

class PreferenceFragment : PreferenceFragmentCompat() {

    companion object {
        internal fun applyTheme(theme: String) {
            when (theme) {
                "System" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                "Light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "Dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }

    private fun addMainPreferences() {
        val category = PreferenceCategory(requireActivity())
        category.key = getString(R.string.app_name)
        category.title = getString(R.string.app_name)
        category.isIconSpaceReserved = false
        preferenceScreen.addPreference(category)

        val theme = ListPreference(requireActivity())
        theme.key = getString(R.string.preference_theme_key)
        theme.title = getString(R.string.preference_theme_title)
        theme.entries = resources.getStringArray(R.array.preference_theme_modes)
        theme.entryValues = resources.getStringArray(R.array.preference_theme_mode_keys)
        theme.setValueIndex(0)
        theme.summary = getString(R.string.preference_theme_summary)
        theme.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                applyTheme(newValue as String)
                true
            }
        theme.isIconSpaceReserved = false
        category.addPreference(theme)

        val cache = SeekBarPreference(requireContext())
        cache.key = getString(R.string.cache_size_key)
        cache.title = getString(R.string.cache_size_title)
        cache.summary = getString(R.string.cache_size_summary)
        cache.max = 8192
        cache.min = 512
        cache.showSeekBarValue = true
        cache.isIconSpaceReserved = false
        category.addPreference(cache)
    }

    private fun addPluginPreferences() {
        for ((name, plugin) in PluginManager.plugins) {
            val category = PreferenceCategory(requireActivity())
            category.key = name
            category.title = name
            category.isIconSpaceReserved = false
            preferenceScreen.addPreference(category)
            for (preference in plugin.preferences()) {
                preference.parent?.removePreference(preference)
                if (preference is EditTextPreference) {
                    preference.dialogLayoutResource =
                        if (preference.title == getString(R.string.preference_password_title))
                            R.layout.preference_dialog_edittext_password
                        else R.layout.preference_dialog_edittext
                }
                category.addPreference(preference)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, p1: String?) {
        (activity as? MediaBrowserFragment.MediaFragmentListener)?.let {
            it.setToolbarTitle(getString(R.string.preferences_title))
            it.enableCollapse(false)
            it.showSearchButton(false)
        }
        preferenceScreen = preferenceManager.createPreferenceScreen(requireActivity())
        addMainPreferences()
        addPluginPreferences()
    }
}