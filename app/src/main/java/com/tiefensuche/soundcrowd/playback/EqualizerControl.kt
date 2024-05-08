package com.tiefensuche.soundcrowd.playback

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.os.Build
import android.preference.PreferenceManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.tiefensuche.soundcrowd.ui.preferences.EqualizerFragment

internal class EqualizerControl {

    companion object {

        private val TAG = EqualizerFragment::class.simpleName

        const val CONFIG_EQUALIZER_ENABLED = "config.equalizer.enabled"
        const val CONFIG_PRESET = "config.equalizer.preset"
        const val CONFIG_BASSBOOST_ENABLED = "config.bassboost.enabled"
        const val CONFIG_BASSBOOST_STRENGTH = "config.bassboost.strength"
        const val CONFIG_LOUDNESS_ENABLED = "config.loudness.enabled"
        const val CONFIG_LOUDNESS_GAIN = "config.loudness.gain"
        const val CONFIG_REVERB_ENABLED = "config.reverb.enabled"
        const val CONFIG_REVERB_PRESET = "config.reverb.preset"

        internal var mEqualizer: Equalizer? = null
        internal var mBassBoost: BassBoost? = null
        internal var mPresetReverb: PresetReverb? = null
        internal var mLoudnessEnhancer: LoudnessEnhancer? = null

        private var audioSessionId: Int = 0

        internal fun setupEqualizerFX(audioSessionId: Int, context: Context) {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            if (audioSessionId != 0 && EqualizerControl.audioSessionId != audioSessionId) {
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
                    Log.e(TAG, "error while enabling audio effect", e)
                }
            }
        }

        internal fun setEqualizer(preset: Short) {
            if (mEqualizer == null) {
                mEqualizer = Equalizer(Integer.MAX_VALUE, audioSessionId)
            }
            mEqualizer?.enabled = true
            mEqualizer?.usePreset(preset)
        }

        internal fun setBassBoost(strength: Short) {
            if (mBassBoost == null) {
                mBassBoost = BassBoost(Integer.MAX_VALUE, audioSessionId)
            }
            mBassBoost?.enabled = true
            mBassBoost?.setStrength(strength)
        }

        internal fun setReverb(preset: Short) {
            if (mPresetReverb == null) {
                mPresetReverb = PresetReverb(Integer.MAX_VALUE, audioSessionId)
            }
            mPresetReverb?.enabled = true
            mPresetReverb?.preset = preset
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        internal fun setLoudness(targetGain: Int) {
            if (mLoudnessEnhancer == null) {
                mLoudnessEnhancer = LoudnessEnhancer(audioSessionId)
            }
            mLoudnessEnhancer?.enabled = true
            mLoudnessEnhancer?.setTargetGain(targetGain)
        }

        internal fun releaseAudioEffect(audioEffect: AudioEffect?) {
            try {
                if (audioEffect?.enabled == true) {
                    audioEffect.enabled = false
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "can not release audio effect", e)
            }
        }
    }
}