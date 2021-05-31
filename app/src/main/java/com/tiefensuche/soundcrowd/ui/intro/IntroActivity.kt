/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui.intro

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.github.appintro.model.SliderPage
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.MusicPlayerActivity
import com.tiefensuche.soundcrowd.ui.SplashActivity.Companion.FIRST_START

/**
 * Intro that will be shown on first start to introduce into the activity
 *
 * Created by tiefensuche on 26.02.18.
 */
internal class IntroActivity : AppIntro() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var sliderPage = SliderPage()
        sliderPage.title = getString(R.string.intro_welcome_title)
        sliderPage.description = getString(R.string.intro_welcome_text)
        sliderPage.imageDrawable = R.drawable.intro_waveform
        sliderPage.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
        addSlide(AppIntroFragment.newInstance(sliderPage))

        sliderPage = SliderPage()
        sliderPage.title = getString(R.string.intro_welcome_cue_points_title)
        sliderPage.description = getString(R.string.intro_welcome_cue_points_text)
        sliderPage.imageDrawable = R.drawable.intro_cuepoint
        sliderPage.backgroundColor = ContextCompat.getColor(this, R.color.colorPrimary)
        addSlide(AppIntroFragment.newInstance(sliderPage))

        sliderPage = SliderPage()
        sliderPage.title = getString(R.string.intro_welcome_local_music_title)
        sliderPage.description = getString(R.string.intro_welcome_local_music_text)
        sliderPage.imageDrawable = R.drawable.files_icon
        sliderPage.backgroundColor = ContextCompat.getColor(this, R.color.intro_files)
        addSlide(AppIntroFragment.newInstance(sliderPage))

        askForPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 3)

        isSkipButtonEnabled = false
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(FIRST_START, false).apply()
        startActivity(Intent(this, MusicPlayerActivity::class.java))
        finish()
    }
}