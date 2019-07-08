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
import com.tiefensuche.soundcrowd.extensions.WebRequests
import com.tiefensuche.soundcrowd.images.SoundCrowdGlideModule
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
                val jsonObject = JSONObject(WebRequests.get(key).value)
                jsonObject.getJSONArray("samples")
            } else {
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
        // nothing
    }

    override fun cancel() {
        // nothing
    }

    override fun getDataClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }
}