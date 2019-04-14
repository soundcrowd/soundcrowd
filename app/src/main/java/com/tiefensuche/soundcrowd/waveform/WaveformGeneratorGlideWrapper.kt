/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.waveform

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.ringdroid.soundfile.SoundFile
import com.tiefensuche.soundcrowd.images.SoundCrowdGlideModule
import com.tiefensuche.soundcrowd.utils.LogHelper
import com.tiefensuche.soundcrowd.utils.Utils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.IOException

internal class WaveformGeneratorGlideWrapper(private val context: Context, private val params: StringKey, private val width: Int, private val height: Int) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {
            val key = params.key
            val jsonArray: JSONArray
            jsonArray = if (key.startsWith("http")) {
                val jsonObject = JSONObject(Utils.fetchFromUrl(key, null))
                jsonObject.getJSONArray("samples")
            } else {
                LogHelper.d(TAG, "key=", key, "path=", Uri.parse(key).path, ", type=", context.contentResolver.getType(Uri.parse(key)))
                WaveformExtractor.extractWaveform(context, Uri.parse(key))
            }
            val waveform = WaveformGenerator.generateWaveform(jsonArray, width, height, SoundCrowdGlideModule.barWidth, SoundCrowdGlideModule.barGap, SoundCrowdGlideModule.bottomBorder)
            callback.onDataReady(waveform)
        } catch (e: JSONException) {
            callback.onLoadFailed(e)
        } catch (e: IOException) {
            callback.onLoadFailed(e)
        } catch (e: SoundFile.InvalidInputException) {
            callback.onLoadFailed(e)
        }

    }

    override fun cleanup() {

    }

    override fun cancel() {

    }


    override fun getDataClass(): Class<Bitmap> {
        return Bitmap::class.java
    }


    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(WaveformGeneratorGlideWrapper::class.java)
    }
}