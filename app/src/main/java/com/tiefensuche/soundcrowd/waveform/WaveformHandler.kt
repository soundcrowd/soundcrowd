/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.waveform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v4.media.MediaMetadataCompat
import android.view.View
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.database.Database
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.images.GlideRequests

internal class WaveformHandler(private val waveformView: WaveformView) {

    private val cuePoint: Bitmap = BitmapFactory.decodeResource(waveformView.resources,
            R.drawable.ic_star_on)
    private val play: Bitmap = BitmapFactory.decodeResource(waveformView.resources,
            R.drawable.ic_play_arrow_black_36dp)

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
            val lastPosition = Database.instance.getLastPosition(metadata.description.mediaId)
            if (lastPosition > 0) {
                waveformView.drawCuePoint(CuePoint(it, lastPosition.toInt(), waveformView.context.getString(R.string.last_position)), duration, play)
            }
            for (cuePoint in Database.instance.getCuePoints(it)) {
                waveformView.drawCuePoint(cuePoint, duration, this.cuePoint)
            }
        }
    }

    internal fun addCuePoint(metadata: MediaMetadataCompat, position: Int, duration: Int, text: String = "") {
        metadata.description.mediaId?.let {
            Database.instance.addCuePoint(metadata, position, text)
            waveformView.drawCuePoint(CuePoint(it, position, text), duration, cuePoint)
        }
    }
}