/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import android.content.Context
import android.support.v4.media.MediaDescriptionCompat
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
internal class ArtworkLoader(private val context: Context) : ModelLoader<MediaDescriptionCompat, ByteBuffer> {

    override fun buildLoadData(model: MediaDescriptionCompat, width: Int, height: Int, options: Options): ModelLoader.LoadData<ByteBuffer>? {
        return model.mediaId?.let { ModelLoader.LoadData(StringKey(it), ArtworkExtractor(context, model)) }
    }

    override fun handles(description: MediaDescriptionCompat): Boolean {
        return !(description.iconUri?.toString()?.startsWith("http") ?: true)
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<MediaDescriptionCompat, ByteBuffer> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<MediaDescriptionCompat, ByteBuffer> {
            return ArtworkLoader(context)
        }

        override fun teardown() {
            // nothing
        }
    }
}