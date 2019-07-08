/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.waveform

import android.graphics.Bitmap

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource

/**
 * Created by tiefensuche on 06.02.18.
 */

internal class WaveformResourceDecoder(private val pool: BitmapPool) : ResourceDecoder<Bitmap, Bitmap> {

    override fun handles(source: Bitmap, options: Options): Boolean {
        return true
    }

    override fun decode(source: Bitmap, width: Int, height: Int, options: Options): Resource<Bitmap>? {
        return BitmapResource.obtain(source, pool)
    }
}