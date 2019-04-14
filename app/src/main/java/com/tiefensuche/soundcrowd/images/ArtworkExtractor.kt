/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import android.content.Context
import android.media.MediaMetadataRetriever
import android.support.v4.media.MediaDescriptionCompat

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.tiefensuche.soundcrowd.utils.LogHelper

import java.nio.ByteBuffer

/**
 * Extract artworks from music files via [MediaMetadataRetriever] within [com.bumptech.glide.Glide]
 */
class ArtworkExtractor internal constructor(private val context: Context, private val description: MediaDescriptionCompat) : DataFetcher<ByteBuffer> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ByteBuffer>) {
        LogHelper.d(TAG, "trying to extract embedded image from music file")
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
            LogHelper.w(TAG, e, "failed to get embedded picture")
            callback.onLoadFailed(e)
        }

    }

    override fun cleanup() {

    }

    override fun cancel() {

    }


    override fun getDataClass(): Class<ByteBuffer> {
        return ByteBuffer::class.java
    }


    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(ArtworkExtractor::class.java)
    }
}