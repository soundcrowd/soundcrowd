/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui.intro

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment.Companion.createInstance
import com.github.appintro.model.SliderPage
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
        addSlide(createInstance(sliderPage))

        sliderPage = SliderPage()
        sliderPage.title = getString(R.string.intro_welcome_cue_points_title)
        sliderPage.description = getString(R.string.intro_welcome_cue_points_text)
        sliderPage.imageDrawable = R.drawable.intro_cuepoint
        addSlide(createInstance(sliderPage))

        sliderPage = SliderPage()
        sliderPage.title = getString(R.string.intro_welcome_local_music_title)
        sliderPage.description = getString(R.string.intro_welcome_local_music_text)
        sliderPage.imageDrawable = R.drawable.files_icon
        addSlide(createInstance(sliderPage))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askForPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 3)
        } else {
            askForPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 3)
        }

        isSkipButtonEnabled = false
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        startActivity(Intent(this, MusicPlayerActivity::class.java))
        finish()
    }
}