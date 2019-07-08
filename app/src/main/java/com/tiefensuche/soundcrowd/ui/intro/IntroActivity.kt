/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui.intro

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.AppIntroFragment
import com.github.paolorotolo.appintro.model.SliderPage
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.MusicPlayerActivity

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
        sliderPage.bgColor = ContextCompat.getColor(this, R.color.colorPrimary)
        addSlide(AppIntroFragment.newInstance(sliderPage))

        sliderPage = SliderPage()
        sliderPage.title = getString(R.string.intro_welcome_cue_points_title)
        sliderPage.description = getString(R.string.intro_welcome_cue_points_text)
        sliderPage.imageDrawable = R.drawable.intro_cuepoint
        sliderPage.bgColor = ContextCompat.getColor(this, R.color.colorPrimary)
        addSlide(AppIntroFragment.newInstance(sliderPage))

        sliderPage = SliderPage()
        sliderPage.title = getString(R.string.intro_welcome_local_music_title)
        sliderPage.description = getString(R.string.intro_welcome_local_music_text)
        sliderPage.imageDrawable = R.drawable.files_icon
        sliderPage.bgColor = ContextCompat.getColor(this, R.color.intro_files)
        addSlide(AppIntroFragment.newInstance(sliderPage))

        askForPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 3)

        showSkipButton(false)
    }

    override fun onDonePressed() {
        super.onDonePressed()
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("first_start", false).apply()
        startActivity(Intent(this, MusicPlayerActivity::class.java))
        finish()
    }
}