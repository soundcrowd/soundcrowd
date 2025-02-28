/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import androidx.media3.common.MediaItem
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import java.io.InputStream

/**
 * Download artworks from remote from given url via [com.bumptech.glide.Glide] based
 * on [BaseGlideUrlLoader]
 */
internal class RemoteArtworkLoader private constructor(urlLoader: ModelLoader<GlideUrl, InputStream>) : BaseGlideUrlLoader<MediaItem>(urlLoader) {

    override fun getUrl(descriptionCompat: MediaItem, width: Int, height: Int, options: Options): String {
        return descriptionCompat.mediaMetadata.artworkUri.toString()
    }

    override fun handles(description: MediaItem): Boolean {
        return description.mediaMetadata.artworkUri.toString().startsWith("http")
    }

    class Factory : ModelLoaderFactory<MediaItem, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<MediaItem, InputStream> {
            return RemoteArtworkLoader(multiFactory.build(GlideUrl::class.java, InputStream::class.java))
        }

        override fun teardown() {
            // Do nothing.
        }
    }
}