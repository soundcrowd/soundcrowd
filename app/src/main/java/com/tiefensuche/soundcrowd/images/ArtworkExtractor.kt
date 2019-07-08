/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import android.content.Context
import android.media.MediaMetadataRetriever
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

import java.nio.ByteBuffer

/**
 * Extract artworks from music files via [MediaMetadataRetriever] within [com.bumptech.glide.Glide]
 */
internal class ArtworkExtractor internal constructor(private val context: Context, private val description: MediaDescriptionCompat) : DataFetcher<ByteBuffer> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ByteBuffer>) {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, description.iconUri)
            val data = mmr.embeddedPicture
            mmr.release()

            if (data != null) {
                callback.onDataReady(ByteBuffer.wrap(data))
            } else {
                callback.onLoadFailed(Exception("no embedded picture in music file"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to get embedded picture", e)
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        // nothing
    }

    override fun cancel() {
        // nothing
    }

    override fun getDataClass(): Class<ByteBuffer> {
        return ByteBuffer::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }

    companion object {
        private val TAG = ArtworkExtractor::class.simpleName
    }
}