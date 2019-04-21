package com.tiefensuche.soundcrowd.playback

import android.content.Context
import android.media.audiofx.*
import android.os.Build
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import com.tiefensuche.soundcrowd.ui.preferences.EqualizerFragment
import com.tiefensuche.soundcrowd.utils.LogHelper

class EqualizerControl {

    companion object {

        private val TAG = LogHelper.makeLogTag(EqualizerFragment::class.java)

        const val CONFIG_EQUALIZER_ENABLED = "config.equalizer.enabled"
        const val CONFIG_PRESET = "config.equalizer.preset"
        const val CONFIG_BASSBOOST_ENABLED = "config.bassboost.enabled"
        const val CONFIG_BASSBOOST_STRENGTH = "config.bassboost.strength"
        const val CONFIG_LOUDNESS_ENABLED = "config.loudness.enabled"
        const val CONFIG_LOUDNESS_GAIN = "config.loudness.gain"
        const val CONFIG_REVERB_ENABLED = "config.reverb.enabled"
        const val CONFIG_REVERB_PRESET = "config.reverb.preset"

        var mEqualizer: Equalizer? = null
        var mBassBoost: BassBoost? = null
        var mPresetReverb: PresetReverb? = null
        var mLoudnessEnhancer: LoudnessEnhancer? = null

        private var audioSessionId: Int = 0

        fun setupEqualizerFX(audioSessionId: Int, context: Context) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (audioSessionId != 0 && EqualizerControl.audioSessionId != audioSessionId) {
                LogHelper.d(TAG, "update audio session id and recreate effects")
                EqualizerControl.audioSessionId = audioSessionId
                try {
                    if (preferences.getBoolean(CONFIG_EQUALIZER_ENABLED, false)) {
                        setEqualizer(preferences.getInt(CONFIG_PRESET, 0).toShort())
                    }
                    if (preferences.getBoolean(CONFIG_BASSBOOST_ENABLED, false)) {
                        setBassBoost(preferences.getInt(CONFIG_BASSBOOST_STRENGTH, 0).toShort())
                    }
                    if (preferences.getBoolean(CONFIG_REVERB_ENABLED, false)) {
                        setReverb(preferences.getInt(CONFIG_REVERB_PRESET, 0).toShort())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && preferences.getBoolean(CONFIG_LOUDNESS_ENABLED, false)) {
                        setLoudness(preferences.getInt(CONFIG_LOUDNESS_GAIN, 0))
                    }
                } catch (e: RuntimeException) {
                    LogHelper.e(TAG, "error while enabling audio effect", e)
                }
            }
        }

        fun setEqualizer(preset: Short) {
            if (mEqualizer == null) {
                mEqualizer = Equalizer(Integer.MAX_VALUE, audioSessionId)
            }
            mEqualizer?.enabled = true
            mEqualizer?.usePreset(preset)
        }

        fun setBassBoost(strength: Short) {
            if (mBassBoost == null) {
                mBassBoost = BassBoost(Integer.MAX_VALUE, audioSessionId)
            }
            mBassBoost?.setStrength(strength)
            mBassBoost?.enabled = true
        }

        fun setReverb(preset: Short) {
            if (mPresetReverb == null) {
                mPresetReverb = PresetReverb(Integer.MAX_VALUE, audioSessionId)
            }
            mPresetReverb?.enabled = true
            mPresetReverb?.preset = preset
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        fun setLoudness(targetGain: Int) {
            if (mLoudnessEnhancer == null) {
                mLoudnessEnhancer = LoudnessEnhancer(audioSessionId)
            }
            mLoudnessEnhancer?.enabled = true
            mLoudnessEnhancer?.setTargetGain(targetGain)
        }

        fun releaseAudioEffect(audioEffect: AudioEffect?) {
            try {
                if (audioEffect?.enabled == true) {
                    audioEffect.enabled = false
                }
            } catch (e: RuntimeException) {
                LogHelper.e(TAG, "can not release audio effect", e)
            }
        }
    }

}