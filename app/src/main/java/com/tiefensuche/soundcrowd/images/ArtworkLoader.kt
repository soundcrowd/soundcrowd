/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import android.content.Context
import androidx.media3.common.MediaItem
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.tiefensuche.soundcrowd.waveform.StringKey
import java.nio.ByteBuffer

/**
 * ModelLoader for loading artworks of local music files via [com.bumptech.glide.Glide]
 *
 *
 * Created by tiefensuche on 6/29/17.
 */
internal class ArtworkLoader(private val context: Context) : ModelLoader<MediaItem, ByteBuffer> {

    override fun buildLoadData(model: MediaItem, width: Int, height: Int, options: Options): ModelLoader.LoadData<ByteBuffer> {
        return ModelLoader.LoadData(StringKey(model.mediaId), ArtworkExtractor(context, model))
    }

    override fun handles(description: MediaItem): Boolean {
        return !description.mediaMetadata.artworkUri.toString().startsWith("http")
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<MediaItem, ByteBuffer> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<MediaItem, ByteBuffer> {
            return ArtworkLoader(context)
        }

        override fun teardown() {
            // nothing
        }
    }
}