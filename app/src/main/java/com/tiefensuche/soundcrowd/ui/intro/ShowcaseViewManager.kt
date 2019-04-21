/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui.intro

import android.app.Activity
import android.graphics.Point
import android.preference.PreferenceManager
import android.view.MotionEvent
import android.view.View
import com.github.amlcurran.showcaseview.OnShowcaseEventListener
import com.github.amlcurran.showcaseview.ShowcaseView
import com.github.amlcurran.showcaseview.targets.Target
import com.github.amlcurran.showcaseview.targets.ViewTarget
import com.tiefensuche.soundcrowd.R

/**
 * Introduces some of the features of the app
 *
 * Created by tiefensuche on 26.02.18.
 */
object ShowcaseViewManager {

    private var blocked = false

    fun introduce(function: ShowcaseFunction, activity: Activity) {
        introduce(function, ViewTarget(activity.findViewById<View>(function.id)), activity)
    }

    fun introduce(function: ShowcaseFunction, point: Point, activity: Activity) {
        introduce(function, object: Target {
            override fun getPoint(): Point {
                return point
            }
        }, activity)
    }

    private fun introduce(function: ShowcaseFunction, target: Target, activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        if (!blocked && !prefs.getBoolean(function.function, false)) {
            blocked = true
            ShowcaseView.Builder(activity)
                    .withMaterialShowcase()
                    .setTarget(target)
                    .setContentTitle(function.title)
                    .setContentText(function.text)
                    .setStyle(R.style.CustomShowcaseTheme)
                    .hideOnTouchOutside()
                    .setShowcaseEventListener(object : OnShowcaseEventListener {
                        override fun onShowcaseViewHide(showcaseView: ShowcaseView) {}

                        override fun onShowcaseViewDidHide(showcaseView: ShowcaseView) {
                            blocked = false
                        }

                        override fun onShowcaseViewShow(showcaseView: ShowcaseView) {

                        }

                        override fun onShowcaseViewTouchBlocked(motionEvent: MotionEvent) {

                        }
                    }).build()
            prefs.edit().putBoolean(function.function, true).apply()
        }
    }

    enum class ShowcaseFunction constructor(internal val function: String, internal val id: Int, internal val title: Int, internal val text: Int) {
        SLIDING_UP("SLIDING_UP", R.id.controls, R.string.showcase_sliding_up_title, R.string.showcase_sliding_up_text),
        WAVEFORM_SEEKING("WAVEFORM_SEEKING", R.id.waveformView, R.string.showcase_waveform_seek_title, R.string.showcase_waveform_seek_text),
        CUE_POINT("CUE_POINT", R.id.star, R.string.showcase_cue_points_title, R.string.showcase_cue_points_text),
        SEARCH_VIEW("SEARCH_VIEW", -1, R.string.showcase_search_title, R.string.showcase_search_text)
    }
}
