/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui.preferences

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import android.support.v4.app.Fragment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.tiefensuche.soundcrowd.R
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

    private var bandBars: MutableList<SeekBar> = ArrayList()

    private fun setupBands(layout: LinearLayout) {
        val context = layout.context

        // Create the Equalizer object (an AudioEffect subclass) and attach it to our media player,
        // with a default priority (0).

        val bands = mEqualizer!!.numberOfBands

        val minEQLevel = mEqualizer!!.bandLevelRange[0]
        val maxEQLevel = mEqualizer!!.bandLevelRange[1]

        bandBars = ArrayList()

        for (i in 0 until bands) {

            val row = LinearLayout(context)
            row.orientation = LinearLayout.VERTICAL
            val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
            params.leftMargin = 10
            params.rightMargin = 10
            row.layoutParams = params

            val freqTextView = TextView(context)
            freqTextView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            freqTextView.gravity = Gravity.CENTER
            freqTextView.text = (mEqualizer!!.getCenterFreq(i.toShort()) / 1000).toString() + " Hz"

            val minDbTextView = TextView(context)
            minDbTextView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            minDbTextView.gravity = Gravity.CENTER
            minDbTextView.text = (minEQLevel / 100).toString() + " dB"

            val maxDbTextView = TextView(context)
            maxDbTextView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            maxDbTextView.gravity = Gravity.CENTER
            maxDbTextView.text = (maxEQLevel / 100).toString() + " dB"

            val layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            layoutParams.weight = 1f
            layoutParams.gravity = Gravity.CENTER

            val bar = VerticalSeekBar(context)
            bar.layoutParams = layoutParams
            bar.max = maxEQLevel - minEQLevel
            bar.progress = mEqualizer!!.getBandLevel(i.toShort()) - minEQLevel

            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int,
                                               fromUser: Boolean) {
                    try {
                        if (mEqualizer != null && mEqualizer!!.enabled) {
                            mEqualizer!!.setBandLevel(i.toShort(), (progress + minEQLevel).toShort())
                        }
                    } catch (e: RuntimeException) {
                        LogHelper.d(TAG, "can not set band " + i + " to " + (progress + minEQLevel), e)
                    }

                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
            bandBars.add(bar)

            row.addView(maxDbTextView)
            row.addView(bar)
            row.addView(minDbTextView)
            row.addView(freqTextView)
            layout.addView(row)
        }
    }

    private fun setupBassBoost(seekBar: SeekBar, preferences: SharedPreferences) {
        seekBar.progress = preferences.getInt(CONFIG_BASSBOOST_STRENGTH, 0)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                preferences.edit().putInt(CONFIG_BASSBOOST_STRENGTH, i).apply()
                try {
                    if (mBassBoost != null && mBassBoost!!.enabled) {
                        LogHelper.d(TAG, "set bass boost to level ", i)
                        mBassBoost!!.setStrength(i.toShort())
                    }
                } catch (e: RuntimeException) {
                    LogHelper.e(TAG, "can not set bass boost strength", e)
                }

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {

            }
        })
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private fun setupLoudnessEnhancer(seekBar: SeekBar, preferences: SharedPreferences) {
        seekBar.progress = preferences.getInt(CONFIG_LOUDNESS_GAIN, 0)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                preferences.edit().putInt(CONFIG_LOUDNESS_GAIN, i).apply()
                try {
                    if (mLoudnessEnhancer != null && mLoudnessEnhancer!!.enabled) {
                        LogHelper.d(TAG, "set loudness enhancer to target gain ", i)
                        mLoudnessEnhancer!!.setTargetGain(i)
                    }
                } catch (e: RuntimeException) {
                    LogHelper.e(TAG, "can not set loudness enhancer target gain", e)
                }

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {

            }
        })
    }

    private fun updateBands() {
        try {
            val minEQLevel = mEqualizer!!.bandLevelRange[0]
            for (i in bandBars.indices) {
                bandBars[i].progress = mEqualizer!!.getBandLevel(i.toShort()) - minEQLevel
            }
        } catch (e: RuntimeException) {
            LogHelper.e(TAG, "can not update bands", e)
        }

    }

    private fun setEnabledBands(enabled: Boolean) {
        for (bar in bandBars) {
            bar.isEnabled = enabled
        }
    }

    private fun loadPresets(spinner: Spinner, preferences: SharedPreferences) {
        val adapter = ArrayAdapter<String>(spinner.context, android.R.layout.simple_spinner_item)
        for (i in 0 until mEqualizer!!.numberOfPresets) {
            LogHelper.d(TAG, "preset available: ", mEqualizer!!.getPresetName(i.toShort()))
            adapter.add(mEqualizer!!.getPresetName(i.toShort()))
        }
        spinner.adapter = adapter
        spinner.setSelection(preferences.getInt(CONFIG_PRESET, 0))
    }

    private fun setupEqualizer(spinner: Spinner, checkBox: CheckBox, layout: LinearLayout, preferences: SharedPreferences) {
        checkBox.isChecked = preferences.getBoolean(CONFIG_EQUALIZER_ENABLED, false)
        checkBox.setOnCheckedChangeListener { compoundButton, b ->
            preferences.edit().putBoolean(CONFIG_EQUALIZER_ENABLED, b).apply()
            spinner.isEnabled = b
            if (b) {
                setupEqualizer(preferences.getInt(CONFIG_PRESET, 0).toShort())
                setEnabledBands(true)
            } else {
                setEnabledBands(false)
                releaseEqualizer()
            }
        }
        spinner.isEnabled = checkBox.isChecked
        try {
            if (mEqualizer == null) {
                mEqualizer = Equalizer(0, 0)
            }
            setupBands(layout)
            setEnabledBands(checkBox.isChecked)
            loadPresets(spinner, preferences)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                    try {
                        if (mEqualizer != null && mEqualizer!!.enabled) {
                            LogHelper.d(TAG, "use preset: ", mEqualizer!!.getPresetName(i.toShort()))
                            preferences.edit().putInt(CONFIG_PRESET, i).apply()
                            mEqualizer!!.usePreset(i.toShort())
                            updateBands()
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

    private fun setupBassBoost(checkBox: CheckBox, seekBar: SeekBar, preferences: SharedPreferences) {
        checkBox.isChecked = preferences.getBoolean(CONFIG_BASSBOOST_ENABLED, false)
        checkBox.setOnCheckedChangeListener { compoundButton, b ->
            LogHelper.d(TAG, "bass boost enable: ", b)
            preferences.edit().putBoolean(CONFIG_BASSBOOST_ENABLED, b).apply()
            if (b) {
                setupBassBoost(preferences.getInt(CONFIG_BASSBOOST_STRENGTH, 0).toShort())
            } else {
                releaseBassBoost()
            }
            seekBar.isEnabled = b
        }
        setupBassBoost(seekBar, preferences)
        seekBar.isEnabled = checkBox.isChecked
    }

    private fun setupReverb(spinner: Spinner, checkBox: CheckBox, preferences: SharedPreferences) {
        checkBox.isChecked = preferences.getBoolean(CONFIG_REVERB_ENABLED, false)
        checkBox.setOnCheckedChangeListener { compoundButton, b ->
            preferences.edit().putBoolean(CONFIG_REVERB_ENABLED, b).apply()
            spinner.isEnabled = b
            if (b) {
                setupReverb(preferences.getInt(CONFIG_REVERB_PRESET, 0).toShort())
            } else {
                releaseReverb()
            }
        }
        spinner.isEnabled = checkBox.isChecked
        val adapter = ArrayAdapter<String>(spinner.context, android.R.layout.simple_spinner_item)
        adapter.addAll("NONE", "SMALLROOM", "MEDIUMROOM", "LARGEROOM", "MEDIUMHALL", "LARGEHALL", "PLATE")
        spinner.adapter = adapter
        spinner.setSelection(preferences.getInt(CONFIG_REVERB_PRESET, 0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                preferences.edit().putInt(CONFIG_REVERB_PRESET, i).apply()
                try {
                    if (mPresetReverb != null && mPresetReverb!!.enabled) {
                        mPresetReverb!!.preset = i.toShort()
                    }
                } catch (e: RuntimeException) {
                    LogHelper.e(TAG, "can not set preset for reverb", e)
                }

            }

            override fun onNothingSelected(adapterView: AdapterView<*>) {

            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private fun setupLoudnessEnhancer(checkBox: CheckBox, seekBar: SeekBar, preferences: SharedPreferences) {
        checkBox.visibility = View.VISIBLE
        checkBox.isChecked = preferences.getBoolean(CONFIG_LOUDNESS_ENABLED, false)
        checkBox.setOnCheckedChangeListener { compoundButton, b ->
            LogHelper.d(TAG, "loudness enhancer enable: ", b)
            preferences.edit().putBoolean(CONFIG_LOUDNESS_ENABLED, b).apply()
            if (b) {
                setupLoudnessEnhancer(0)
            } else {
                releaseLoudnessEnhancer()
            }
            seekBar.isEnabled = b
        }
        setupLoudnessEnhancer(seekBar, preferences)
        seekBar.isEnabled = checkBox.isChecked
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val preferences = (activity as MusicPlayerActivity).preferences
        val rootView = inflater.inflate(R.layout.fragment_equalizer, container, false)
        setupEqualizer(rootView.findViewById(R.id.spinnerPreset), rootView.findViewById(R.id.checkBoxEqualizer), rootView.findViewById(R.id.layoutEqualizer), preferences)
        setupBassBoost(rootView.findViewById(R.id.checkBoxBassBoost), rootView.findViewById(R.id.seekBarBassBoost), preferences)
        setupReverb(rootView.findViewById(R.id.spinnerReverb), rootView.findViewById(R.id.checkBoxReverb), preferences)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            rootView.findViewById<View>(R.id.areaLoudness).visibility = View.VISIBLE
            setupLoudnessEnhancer(rootView.findViewById(R.id.checkBoxLoudness), rootView.findViewById(R.id.seekBarLoudness), preferences)
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

    companion object {

        const val FRAGMENT_TAG = "soundcrowd_equalizer_container"
        private const val CONFIG_EQUALIZER_ENABLED = "config.equalizer.enabled"
        private const val CONFIG_PRESET = "config.equalizer.preset"
        private const val CONFIG_BASSBOOST_ENABLED = "config.bassboost.enabled"
        private const val CONFIG_BASSBOOST_STRENGTH = "config.bassboost.strength"
        private const val CONFIG_LOUDNESS_ENABLED = "config.loudness.enabled"
        private const val CONFIG_LOUDNESS_GAIN = "config.loudness.gain"
        private const val CONFIG_REVERB_ENABLED = "config.reverb.enabled"
        private const val CONFIG_REVERB_PRESET = "config.reverb.preset"
        private val TAG = LogHelper.makeLogTag(EqualizerFragment::class.java)

        private var mEqualizer: Equalizer? = null

        private var mBassBoost: BassBoost? = null

        private var mPresetReverb: PresetReverb? = null

        private var mLoudnessEnhancer: LoudnessEnhancer? = null
        private var audioSessionId: Int = 0

        fun setupEqualizerFX(audioSessionId: Int, context: Context) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (audioSessionId != 0 && EqualizerFragment.audioSessionId != audioSessionId) {
                LogHelper.d(TAG, "update audio session id and recreate effects")
                release()
                EqualizerFragment.audioSessionId = audioSessionId
                if (preferences!!.getBoolean(CONFIG_EQUALIZER_ENABLED, false)) {
                    setupEqualizer(preferences.getInt(CONFIG_PRESET, 0).toShort())
                }
                if (preferences.getBoolean(CONFIG_BASSBOOST_ENABLED, false)) {
                    setupBassBoost(preferences.getInt(CONFIG_BASSBOOST_STRENGTH, 0).toShort())
                }
                if (preferences.getBoolean(CONFIG_REVERB_ENABLED, false)) {
                    setupReverb(preferences.getInt(CONFIG_REVERB_PRESET, 0).toShort())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && preferences.getBoolean(CONFIG_LOUDNESS_ENABLED, false)) {
                    setupLoudnessEnhancer(preferences.getInt(CONFIG_LOUDNESS_GAIN, 0))
                }
            }
        }

        private fun setupEqualizer(preset: Short) {
            if (audioSessionId == 0) {
                return
            }
            try {
                mEqualizer = Equalizer(Integer.MAX_VALUE, audioSessionId)
                mEqualizer!!.enabled = true
                LogHelper.d(TAG, "presets=", mEqualizer!!.numberOfPresets, ", current=", mEqualizer!!.currentPreset)
                mEqualizer!!.usePreset(preset)
            } catch (e: RuntimeException) {
                LogHelper.e(TAG, "error while enabling equalizer", e)
            }

        }

        private fun setupBassBoost(strength: Short) {
            if (audioSessionId == 0) {
                return
            }
            try {
                mBassBoost = BassBoost(Integer.MAX_VALUE, audioSessionId)
                mBassBoost!!.enabled = true
                mBassBoost!!.setStrength(strength)
            } catch (e: RuntimeException) {
                LogHelper.w(TAG, "error while enabling bass boost", e)
            }

        }

        private fun setupReverb(preset: Short) {
            if (audioSessionId == 0) {
                return
            }
            try {
                mPresetReverb = PresetReverb(Integer.MAX_VALUE, audioSessionId)
                mPresetReverb!!.enabled = true
                mPresetReverb!!.preset = preset
            } catch (e: RuntimeException) {
                LogHelper.w(TAG, "error while enabling reverb", e)
            }

        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        private fun setupLoudnessEnhancer(targetGain: Int) {
            if (audioSessionId == 0) {
                return
            }
            try {
                mLoudnessEnhancer = LoudnessEnhancer(audioSessionId)
                mLoudnessEnhancer!!.enabled = true
                mLoudnessEnhancer!!.setTargetGain(targetGain)
            } catch (e: RuntimeException) {
                LogHelper.w(TAG, "error while enabling loudness enhancer", e)
            }

        }

        private fun release() {
            releaseEqualizer()
            releaseBassBoost()
            releaseReverb()
            releaseLoudnessEnhancer()
        }

        private fun releaseEqualizer() {
            try {
                if (mEqualizer != null && mEqualizer!!.enabled) {
                    mEqualizer!!.enabled = false
                    mEqualizer!!.release()
                    mEqualizer = null
                }
            } catch (e: RuntimeException) {
                LogHelper.e(TAG, "can not release equalizer", e)
            }

        }

        private fun releaseBassBoost() {
            try {
                if (mBassBoost != null && mBassBoost!!.enabled) {
                    mBassBoost!!.enabled = false
                    mBassBoost!!.release()
                    mBassBoost = null
                }
            } catch (e: RuntimeException) {
                LogHelper.e(TAG, "can not release bass boost", e)
            }

        }

        private fun releaseReverb() {
            try {
                if (mPresetReverb != null && mPresetReverb!!.enabled) {
                    mPresetReverb!!.enabled = false
                    mPresetReverb!!.release()
                    mPresetReverb = null
                }
            } catch (e: RuntimeException) {
                LogHelper.e(TAG, "can not release reverb", e)
            }

        }

        private fun releaseLoudnessEnhancer() {
            try {
                if (mLoudnessEnhancer != null && mLoudnessEnhancer!!.enabled) {
                    mLoudnessEnhancer!!.enabled = false
                    mLoudnessEnhancer!!.release()
                    mLoudnessEnhancer = null
                }
            } catch (e: RuntimeException) {
                LogHelper.e(TAG, "can not release loudness enhancer", e)
            }

        }
    }
}
