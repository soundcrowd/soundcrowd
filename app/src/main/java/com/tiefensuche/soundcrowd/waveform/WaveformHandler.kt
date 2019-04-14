/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.waveform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v4.media.MediaMetadataCompat
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.database.DatabaseHelper
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.sources.MusicProviderSource
import com.tiefensuche.soundcrowd.utils.LogHelper


class WaveformHandler(private val waveformView: WaveformView) {

    private val TAG = LogHelper.makeLogTag(WaveformHandler::class.java)
    private val star: Bitmap
    private val play: Bitmap

    init {
        star = BitmapFactory.decodeResource(waveformView.resources,
                R.drawable.ic_star_on)
        play = BitmapFactory.decodeResource(waveformView.resources,
                R.drawable.ic_play_arrow_black_36dp)
    }

    fun loadWaveform(requests: GlideRequests, metadata: MediaMetadataCompat, duration: Int) {
        if (waveformView.context != null) {
            waveformView.setVisible(false)
            requests.asBitmap()
                    .load(StringKey(metadata.getString(MusicProviderSource.CUSTOM_METADATA_WAVEFORM_URL)))
                    .apply(RequestOptions.overrideOf(waveformView.desiredWidth, waveformView.desiredHeight))
                    .listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                            return false
                        }

                        override fun onResourceReady(resource: Bitmap, model: Any, target: Target<Bitmap>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            waveformView.setWaveform(resource, duration)
                            loadCuePoints(metadata)
                            return true
                        }
                    })
                    .into(waveformView.imageView)
        }
    }


    private fun loadCuePoints(metadata: MediaMetadataCompat) {
        val mediaId = metadata.description.mediaId
        val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
        val lastPosition = DatabaseHelper.instance.getLastPosition(metadata.description.mediaId)
        if (lastPosition > 0) {
            waveformView.drawCuePoint(CuePoint(mediaId!!, lastPosition.toInt(), waveformView.context.getString(R.string.last_position)), duration, play)
        }
        for (cuePoint in DatabaseHelper.instance.getCuePoints(mediaId)) {
            waveformView.drawCuePoint(cuePoint, duration, star)
        }
    }

    fun addCuePoint(metadata: MediaMetadataCompat, position: Int, duration: Int) {
        DatabaseHelper.instance.addCuePoint(metadata, position)
        waveformView.drawCuePoint(CuePoint(metadata.description.mediaId!!, position, ""), duration, star)
    }

    fun addCuePoint(metadata: MediaMetadataCompat, position: Int, duration: Int, description: String) {
        DatabaseHelper.instance.addCuePoint(metadata, position)
        DatabaseHelper.instance.setDescription(metadata.description.mediaId!!, position, description)
        waveformView.drawCuePoint(CuePoint(metadata.description.mediaId!!, position, description), duration, star)
    }
}
