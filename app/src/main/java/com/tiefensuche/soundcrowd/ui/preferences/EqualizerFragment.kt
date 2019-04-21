/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui.preferences

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.v4.app.Fragment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.playback.EqualizerControl
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_BASSBOOST_ENABLED
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_BASSBOOST_STRENGTH
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_EQUALIZER_ENABLED
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_LOUDNESS_ENABLED
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_LOUDNESS_GAIN
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_PRESET
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_REVERB_ENABLED
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_REVERB_PRESET
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.mBassBoost
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.mEqualizer
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.mLoudnessEnhancer
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.mPresetReverb
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.releaseAudioEffect
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.setBassBoost
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.setLoudness
import com.tiefensuche.soundcrowd.ui.MediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.MusicPlayerActivity
import com.tiefensuche.soundcrowd.utils.LogHelper
import java.util.*


/**
 * Equalizer with some effects of the android system
 *
 * Created by tiefensuche on 07.10.16.
 */
class EqualizerFragment : Fragment() {

    private lateinit var mPreferences: SharedPreferences
    private var bandBars: MutableList<SeekBar> = ArrayList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mPreferences = (activity as MusicPlayerActivity).preferences
        val rootView = inflater.inflate(R.layout.fragment_equalizer, container, false)
        setupEqualizer(rootView.findViewById(R.id.spinnerPreset), rootView.findViewById(R.id.checkBoxEqualizer), rootView.findViewById(R.id.layoutEqualizer))
        setupBassBoost(rootView.findViewById(R.id.checkBoxBassBoost), rootView.findViewById(R.id.seekBarBassBoost))
        setupReverb(rootView.findViewById(R.id.spinnerReverb), rootView.findViewById(R.id.checkBoxReverb))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            rootView.findViewById<View>(R.id.areaLoudness).visibility = View.VISIBLE
            setupLoudnessEnhancer(rootView.findViewById(R.id.checkBoxLoudness), rootView.findViewById(R.id.seekBarLoudness))
        }
        return rootView
    }

    override fun onStart() {
        super.onStart()
        val mediaFragmentListener = activity as MediaBrowserFragment.MediaFragmentListener
        mediaFragmentListener.setToolbarTitle(getString(R.string.equalizer_title))
        mediaFragmentListener.showSearchButton(false)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        val mMediaFragmentListener = activity as MediaBrowserFragment.MediaFragmentListener
        mMediaFragmentListener.enableCollapse(false)
    }

    private fun setupBands(layout: LinearLayout) {
        val context = layout.context

        EqualizerControl.mEqualizer?.let {
            val bands = it.numberOfBands

            val minEQLevel = it.bandLevelRange[0]
            val maxEQLevel = it.bandLevelRange[1]

            bandBars = ArrayList()

            for (i in 0 until bands) {
                val row = LinearLayout(context)
                row.orientation = LinearLayout.VERTICAL
                val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
                params.leftMargin = 10
                params.rightMargin = 10
                row.layoutParams = params

                val layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)
                layoutParams.weight = 1f
                layoutParams.gravity = Gravity.CENTER

                val bar = VerticalSeekBar(context)
                bar.layoutParams = layoutParams
                bar.max = maxEQLevel - minEQLevel
                bar.progress = it.getBandLevel(i.toShort()) - minEQLevel

                bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int,
                                                   fromUser: Boolean) {
                        try {
                            if (it.enabled) {
                                it.setBandLevel(i.toShort(), (progress + minEQLevel).toShort())
                            }
                        } catch (e: RuntimeException) {
                            LogHelper.d(TAG, "can not set band " + i + " to " + (progress + minEQLevel), e)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })
                bandBars.add(bar)

                row.addView(createTextView((maxEQLevel / 100).toString() + " dB"))
                row.addView(bar)
                row.addView(createTextView((minEQLevel / 100).toString() + " dB"))
                row.addView(createTextView((it.getCenterFreq(i.toShort()) / 1000).toString() + " Hz"))
                layout.addView(row)
            }
        }
    }

    private fun createTextView(text: String): TextView {
        val textView = TextView(context)
        textView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        textView.gravity = Gravity.CENTER
        textView.text = text
        return textView
    }

    private fun setEnabledBands(enabled: Boolean) {
        for (bar in bandBars) {
            bar.isEnabled = enabled
        }
    }

    private fun loadPresets(spinner: Spinner) {
        mEqualizer?.let {
            val adapter = ArrayAdapter<String>(spinner.context, android.R.layout.simple_spinner_item)
            for (i in 0 until it.numberOfPresets) {
                LogHelper.d(TAG, "preset available: ", it.getPresetName(i.toShort()))
                adapter.add(it.getPresetName(i.toShort()))
            }
            spinner.adapter = adapter
            spinner.setSelection(mPreferences.getInt(CONFIG_PRESET, 0))
        }
    }

    private fun setupEqualizer(spinner: Spinner, checkBox: CheckBox, layout: LinearLayout) {
        setupViews(checkBox, spinner, CONFIG_EQUALIZER_ENABLED, CONFIG_PRESET) { enabled, value ->
            setEnabledBands(enabled)
            if (enabled) {
                EqualizerControl.setEqualizer(value.toShort())
            } else {
                releaseAudioEffect(mEqualizer)
            }
        }

        spinner.isEnabled = checkBox.isChecked
        try {
            if (mEqualizer == null) {
                mEqualizer = Equalizer(0, 0)
            }
            setupBands(layout)
            setEnabledBands(checkBox.isChecked)
            loadPresets(spinner)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                    try {
                        mEqualizer?.let {
                            if (!it.enabled) {
                                return
                            }
                            LogHelper.d(TAG, "use preset: ", it.getPresetName(i.toShort()))
                            mPreferences.edit().putInt(CONFIG_PRESET, i).apply()
                            it.usePreset(i.toShort())
                            val minEQLevel = it.bandLevelRange[0]
                            for (i in bandBars.indices) {
                                bandBars[i].progress = it.getBandLevel(i.toShort()) - minEQLevel
                            }
                        }

                    } catch (e: RuntimeException) {
                        LogHelper.e(TAG, "can not set preset for equalizer", e)
                    }

                }

                override fun onNothingSelected(adapterView: AdapterView<*>) {

                }
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, e)
        }

    }

    private fun setupReverb(spinner: Spinner, checkBox: CheckBox) {
        setupViews(checkBox, spinner, CONFIG_REVERB_ENABLED, CONFIG_REVERB_PRESET) { enabled, value ->
            if (enabled) {
                EqualizerControl.setReverb(value.toShort())
            } else {
                releaseAudioEffect(mPresetReverb)
            }
        }
        val adapter = ArrayAdapter<String>(spinner.context, android.R.layout.simple_spinner_item)
        adapter.addAll("NONE", "SMALLROOM", "MEDIUMROOM", "LARGEROOM", "MEDIUMHALL", "LARGEHALL", "PLATE")
        spinner.adapter = adapter
        spinner.setSelection(mPreferences.getInt(CONFIG_REVERB_PRESET, 0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                mPreferences.edit().putInt(CONFIG_REVERB_PRESET, i).apply()
                try {
                    mPresetReverb?.let {
                        if (!it.enabled) return
                        it.preset = i.toShort()
                    }
                } catch (e: RuntimeException) {
                    LogHelper.e(TAG, "can not set preset for reverb", e)
                }

            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {

            }
        }
    }

    private fun setupBassBoost(checkBox: CheckBox, seekBar: SeekBar) {
        setupViews(checkBox, seekBar, CONFIG_BASSBOOST_ENABLED, CONFIG_BASSBOOST_STRENGTH) { enabled, value ->
            if (enabled) {
                setBassBoost(value.toShort())
            } else {
                releaseAudioEffect(mBassBoost)
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private fun setupLoudnessEnhancer(checkBox: CheckBox, seekBar: SeekBar) {
        checkBox.visibility = View.VISIBLE
        setupViews(checkBox, seekBar, CONFIG_LOUDNESS_ENABLED, CONFIG_LOUDNESS_GAIN) { enabled, value ->
            if (enabled) {
                setLoudness(value)
            } else {
                releaseAudioEffect(mLoudnessEnhancer)
            }
        }
    }

    private fun setupViews(checkBox: CheckBox, seekBar: View, keyEnabled: String, keyValue: String, checkboxFunc: (enabled: Boolean, value: Int) -> Unit) {
        checkBox.isChecked = mPreferences.getBoolean(keyEnabled, false)
        checkBox.setOnCheckedChangeListener { _, b ->
            mPreferences.edit().putBoolean(keyEnabled, b).apply()
            seekBar.isEnabled = b
            checkboxFunc.invoke(b, mPreferences.getInt(keyValue, 0))
        }

        if (seekBar is SeekBar) {
            seekBar.progress = mPreferences.getInt(keyValue, 0)
            seekBar.isEnabled = checkBox.isChecked
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                    mPreferences.edit().putInt(keyValue, i).apply()
                    try {
                        checkboxFunc.invoke(true, i)
                    } catch (e: RuntimeException) {
                        LogHelper.e(TAG, "can not set loudness enhancer target gain", e)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        } else if (seekBar is Spinner) {

        }
    }
}