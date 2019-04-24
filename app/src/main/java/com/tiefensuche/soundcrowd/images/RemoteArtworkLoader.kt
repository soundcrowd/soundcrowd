/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import android.support.v4.media.MediaDescriptionCompat
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
internal class RemoteArtworkLoader private constructor(urlLoader: ModelLoader<GlideUrl, InputStream>) : BaseGlideUrlLoader<MediaDescriptionCompat>(urlLoader) {

    override fun getUrl(descriptionCompat: MediaDescriptionCompat, width: Int, height: Int, options: Options): String {
        return descriptionCompat.iconUri.toString()
    }

    override fun handles(description: MediaDescriptionCompat): Boolean {
        return description.iconUri?.toString()?.startsWith("http") ?: false
    }

    class Factory : ModelLoaderFactory<MediaDescriptionCompat, InputStream> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<MediaDescriptionCompat, InputStream> {
            return RemoteArtworkLoader(multiFactory.build(GlideUrl::class.java, InputStream::class.java))
        }

        override fun teardown() {
            // Do nothing.
        }
    }
}
