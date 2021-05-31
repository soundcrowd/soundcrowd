/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import android.graphics.Bitmap
import androidx.palette.graphics.Palette

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder
import com.bumptech.glide.util.LruCache

/**
 * A [com.bumptech.glide.load.resource.transcode.ResourceTranscoder] for generating
 * [android.support.v7.graphics.Palette]s from [android.graphics.Bitmap]s in the background.
 */
internal class PaletteBitmapTranscoder : ResourceTranscoder<Bitmap, PaletteBitmap> {

    override fun transcode(toTranscode: Resource<Bitmap>, options: Options): Resource<PaletteBitmap>? {
        val bitmap = toTranscode.get()
        val key = bitmap.hashCode()
        colorCache.get(key)?.let {
            return PaletteBitmap(bitmap, it)
        }
        val palette = Palette.Builder(bitmap).generate()
        colorCache.put(key, palette)
        return PaletteBitmap(bitmap, palette)
    }

    companion object {
        private val colorCache = LruCache<Int, Palette>((10 * 1024).toLong())
    }
}