/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.MediaItem
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.tiefensuche.soundcrowd.waveform.StringKey
import com.tiefensuche.soundcrowd.waveform.WaveformModelLoader
import com.tiefensuche.soundcrowd.waveform.WaveformResourceDecoder
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Specific [Glide] Module for the app. Defines some custom image pipelines for
 * waveform generation and artwork loading.
 */
@GlideModule
internal class SoundCrowdGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        super.registerComponents(context, glide, registry)
        val density = context.resources.displayMetrics.density
        barWidth = density.toInt() * 2
        barGap = density.toInt()
        bottomBorder = density.toInt()

        registry
            .append(
                Bitmap::class.java,
                Bitmap::class.java,
                WaveformResourceDecoder(glide.bitmapPool)
            )
            // Waveform generation from JSON and extraction for local files
            .append(
                StringKey::class.java,
                Bitmap::class.java,
                WaveformModelLoader.Factory(context)
            )
            // Artwork directly from media description
            .append(
                MediaItem::class.java,
                ByteArray::class.java,
                MetadataArtworkLoader.Factory()
            )
            // Artwork extraction from local files
            .append(
                MediaItem::class.java,
                ByteBuffer::class.java,
                ArtworkLoader.Factory(context)
            )
            // Remote artwork download
            .append(
                MediaItem::class.java,
                InputStream::class.java,
                RemoteArtworkLoader.Factory()
            )
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setLogLevel(Log.ERROR)
    }

    companion object {
        var barWidth: Int = 0
        var barGap: Int = 0
        var bottomBorder: Int = 0
    }
}