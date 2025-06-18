/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.playback.EqualizerControl
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_BAND_VALUES
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_BASSBOOST_ENABLED
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_BASSBOOST_STRENGTH
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_EQUALIZER_ENABLED
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_LOUDNESS_ENABLED
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_LOUDNESS_GAIN
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_PRESET
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_REVERB_ENABLED
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.CONFIG_REVERB_PRESET
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.audioSessionId
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.mBassBoost
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.mEqualizer
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.mLoudnessEnhancer
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.mPresetReverb
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.releaseAudioEffect
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.setBassBoost
import com.tiefensuche.soundcrowd.playback.EqualizerControl.Companion.setLoudness
import com.tiefensuche.soundcrowd.ui.browser.MediaBrowserFragment

/**
 * Equalizer with some effects of the android system
 *
 * Created by tiefensuche on 07.10.16.
 */
internal class EqualizerFragment : Fragment() {

    private lateinit var mPreferences: SharedPreferences
    private var bandBars: MutableList<SeekBar> = ArrayList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val rootView = inflater.inflate(R.layout.fragment_equalizer, container, false)
        setupEqualizer(rootView.findViewById(R.id.spinnerPreset), rootView.findViewById(R.id.checkBoxEqualizer), rootView.findViewById(R.id.layoutEqualizer))
        setupBassBoost(rootView.findViewById(R.id.checkBoxBassBoost), rootView.findViewById(R.id.seekBarBassBoost))
        setupReverb(rootView.findViewById(R.id.spinnerReverb), rootView.findViewById(R.id.checkBoxReverb))
        rootView.findViewById<View>(R.id.areaLoudness).visibility = View.VISIBLE
        setupLoudnessEnhancer(rootView.findViewById(R.id.checkBoxLoudness), rootView.findViewById(R.id.seekBarLoudness))
        return rootView
    }

    override fun onStart() {
        super.onStart()

        (activity as? MediaBrowserFragment.MediaFragmentListener)?.let {
            it.setToolbarTitle(getString(R.string.equalizer_title))
            it.enableCollapse(false)
            it.showSearchButton(false)
        }
    }

    private fun setupBands(layout: LinearLayout) {
        val context = layout.context

        mEqualizer?.let {
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
                        if (!seekBar.isEnabled)
                            return
                        mEqualizer?.let {
                            try {
                                it.setBandLevel(i.toShort(), (progress + minEQLevel).toShort())
                                mPreferences.edit().putString(
                                    CONFIG_BAND_VALUES,
                                    bandBars.map { (it.progress + minEQLevel).toShort() }.joinToString(",")).apply()
                            } catch (e: RuntimeException) {
                                Log.d(TAG, "can not set band " + i + " to " + (progress + minEQLevel), e)
                            }
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

    private fun loadPresets(context: Context): ArrayAdapter<String> {
        val adapter = ArrayAdapter<String>(context, android.R.layout.simple_spinner_item)
        mEqualizer?.let {
            for (i in 0 until it.numberOfPresets) {
                Log.d(TAG, "preset available: ${it.getPresetName(i.toShort())}")
                adapter.add(it.getPresetName(i.toShort()))
            }
            adapter.add("Custom")
        }
        return adapter
    }

    private fun setupEqualizer(spinner: Spinner, checkBox: CheckBox, layout: LinearLayout) {
        setupBands(layout)
        setupViews(checkBox, spinner, loadPresets(spinner.context), CONFIG_EQUALIZER_ENABLED, CONFIG_PRESET) { enabled, value ->
            if (enabled) {
                EqualizerControl.setEqualizer(value.toShort(), mPreferences.getString(CONFIG_BAND_VALUES, null))
                if (bandBars.isEmpty()) {
                    setupBands(layout)
                    spinner.adapter = loadPresets(spinner.context)
                    spinner.setSelection(mPreferences.getInt(CONFIG_PRESET, 0))
                }
                mEqualizer?.let {
                    setEnabledBands(value.toShort() == it.numberOfPresets)
                    val minEQLevel = it.bandLevelRange[0]
                    for (i in bandBars.indices) {
                        bandBars[i].progress = it.getBandLevel(i.toShort()) - minEQLevel
                    }
                }
            } else {
                setEnabledBands(false)
                releaseAudioEffect(mEqualizer)
                mEqualizer = null
            }
        }
        setEnabledBands(checkBox.isChecked && spinner.selectedItemId.toShort() == mEqualizer?.numberOfPresets)
    }

    private fun setupReverb(spinner: Spinner, checkBox: CheckBox) {
        val adapter = ArrayAdapter<String>(spinner.context, android.R.layout.simple_spinner_item)
        adapter.addAll("NONE", "SMALLROOM", "MEDIUMROOM", "LARGEROOM", "MEDIUMHALL", "LARGEHALL", "PLATE")
        setupViews(checkBox, spinner, adapter, CONFIG_REVERB_ENABLED, CONFIG_REVERB_PRESET) { enabled, value ->
            if (enabled) {
                EqualizerControl.setReverb(value.toShort())
            } else {
                releaseAudioEffect(mPresetReverb)
                mPresetReverb = null
            }
        }
    }

    private fun setupBassBoost(checkBox: CheckBox, seekBar: SeekBar) {
        setupViews(checkBox, seekBar, CONFIG_BASSBOOST_ENABLED, CONFIG_BASSBOOST_STRENGTH) { enabled, value ->
            if (enabled) {
                setBassBoost(value.toShort())
            } else {
                releaseAudioEffect(mBassBoost)
                mBassBoost = null
            }
        }
    }

    private fun setupLoudnessEnhancer(checkBox: CheckBox, seekBar: SeekBar) {
        checkBox.visibility = View.VISIBLE
        setupViews(checkBox, seekBar, CONFIG_LOUDNESS_ENABLED, CONFIG_LOUDNESS_GAIN) { enabled, value ->
            if (enabled) {
                setLoudness(value)
            } else {
                releaseAudioEffect(mLoudnessEnhancer)
                mLoudnessEnhancer = null
            }
        }
    }

    private fun setupViews(checkBox: CheckBox, controlView: View, keyEnabled: String, keyValue: String, checkboxFunc: (enabled: Boolean, value: Int) -> Unit) {
        checkBox.isEnabled = audioSessionId != 0
        checkBox.isChecked = mPreferences.getBoolean(keyEnabled, false)
        controlView.isEnabled = checkBox.isEnabled && checkBox.isChecked
        checkBox.setOnCheckedChangeListener { _, b ->
            mPreferences.edit().putBoolean(keyEnabled, b).apply()
            controlView.isEnabled = b
            checkboxFunc.invoke(b, mPreferences.getInt(keyValue, 0))
        }
    }

    private fun setupViews(checkBox: CheckBox, seekBar: SeekBar, keyEnabled: String, keyValue: String, checkboxFunc: (enabled: Boolean, value: Int) -> Unit) {
        setupViews(checkBox, seekBar as View, keyEnabled, keyValue, checkboxFunc)
        seekBar.progress = mPreferences.getInt(keyValue, 0)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                mPreferences.edit().putInt(keyValue, i).apply()
                try {
                    checkboxFunc.invoke(true, i)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "can not set audio effect value", e)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // nothing
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // nothing
            }
        })
    }

    private fun setupViews(checkBox: CheckBox, spinner: Spinner, adapter: ArrayAdapter<String>, keyEnabled: String, keyValue: String, checkboxFunc: (enabled: Boolean, value: Int) -> Unit) {
        setupViews(checkBox, spinner as View, keyEnabled, keyValue, checkboxFunc)
        spinner.adapter = adapter
        spinner.setSelection(mPreferences.getInt(keyValue, 0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                if (mPreferences.getInt(keyValue, 0) == i) {
                    return
                }
                mPreferences.edit().putInt(keyValue, i).apply()
                try {
                    checkboxFunc.invoke(true, i)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "can not set audio effect value", e)
                }
            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {
                // nothing
            }
        }
    }

    companion object {
        private val TAG = EqualizerFragment::class.simpleName
    }
}