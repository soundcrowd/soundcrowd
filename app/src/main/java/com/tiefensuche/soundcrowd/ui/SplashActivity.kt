/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import com.tiefensuche.soundcrowd.ui.intro.IntroActivity

/**
 * Created by tiefensuche on 26.02.18.
 */

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("first_start", true)) {
            // INTRO SCREEN
            startActivity(Intent(this, IntroActivity::class.java))
        } else {
            startActivity(Intent(this, MusicPlayerActivity::class.java))
        }
        finish()
    }
}
