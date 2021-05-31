/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui.preferences

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.MediaBrowserFragment
import org.json.JSONArray

class PreferenceFragment : PreferenceFragmentCompat() {

    var prefs = HashMap<String, JSONArray>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, p1: String?) {
        (activity as? MediaBrowserFragment.MediaFragmentListener)?.let {
            it.setToolbarTitle(getString(R.string.preferences_title))
            it.enableCollapse(false)
            it.showSearchButton(false)
        }

        preferenceScreen = preferenceManager.createPreferenceScreen(activity)

        for (plugin in prefs.keys) {
            val category = PreferenceCategory(activity)
            category.key = plugin
            category.title = plugin

            preferenceScreen.addPreference(category)

            for (i in 0 until prefs.getValue(plugin).length()) {
                val editText = EditTextPreference(activity)
                val json = prefs.getValue(plugin).getJSONObject(i)
                editText.key = json.getString("key")
                editText.title = json.getString("name")
                editText.summary = json.getString("description")
                editText.dialogTitle = json.getString("name")
                editText.dialogMessage = json.getString("description")
                editText.dialogLayoutResource = if (editText.key == "password") R.layout.preference_dialog_edittext_password else R.layout.preference_dialog_edittext
                category.addPreference(editText)
            }
        }
    }
}