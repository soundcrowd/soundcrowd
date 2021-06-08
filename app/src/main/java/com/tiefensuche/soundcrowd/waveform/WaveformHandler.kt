/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.waveform

import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.view.View
import androidx.core.content.ContextCompat
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.database.Database
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.service.MusicService

internal class WaveformHandler(private val waveformView: WaveformView) {

    private val cuePoint = ContextCompat.getDrawable(waveformView.context, R.drawable.ic_round_star_24)
    private val play = ContextCompat.getDrawable(waveformView.context, R.drawable.ic_round_play_arrow_24)

    internal fun loadWaveform(requests: GlideRequests, metadata: MediaMetadataCompat, duration: Int) {
        if (waveformView.context != null) {
            waveformView.setVisible(View.INVISIBLE)
            val waveform = metadata.getString(MediaMetadataCompatExt.METADATA_KEY_WAVEFORM_URL)
                    ?: return
            requests.asBitmap()
                    .load(StringKey(waveform))
                    .apply(RequestOptions.overrideOf(waveformView.desiredWidth, waveformView.desiredHeight))
                    .listener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                            return false
                        }

                        override fun onResourceReady(resource: Bitmap, model: Any, target: Target<Bitmap>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            waveformView.setWaveform(resource, duration)
                            waveformView.setVisible(View.VISIBLE)
                            loadCuePoints(metadata)
                            return true
                        }
                    })
                    .into(waveformView.imageView)
        }
    }

    private fun loadCuePoints(metadata: MediaMetadataCompat) {
        metadata.description.mediaId?.let {
            val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
            val lastPosition = MusicService.database.getLastPosition(metadata.description.mediaId)
            if (lastPosition > 0) {
                waveformView.drawCuePoint(CuePoint(it, lastPosition.toInt(), waveformView.context.getString(R.string.last_position)), duration, play!!)
            }
            for (cuePoint in MusicService.database.getCuePoints(it)) {
                waveformView.drawCuePoint(cuePoint, duration, this.cuePoint!!)
            }
        }
    }

    internal fun addCuePoint(metadata: MediaMetadataCompat, position: Int, duration: Int, text: String = "") {
        metadata.description.mediaId?.let {
            waveformView.drawCuePoint(CuePoint(it, position, text), duration, cuePoint!!)
        }
    }
}