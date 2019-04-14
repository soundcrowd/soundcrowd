/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import android.graphics.Bitmap
import android.support.v7.graphics.Palette

import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.util.Util

/**
 * A simple wrapper for a [android.support.v7.graphics.Palette] and a [android.graphics.Bitmap].
 */
internal class PaletteBitmap(val bitmap: Bitmap, val palette: Palette) : Resource<PaletteBitmap> {


    override fun getResourceClass(): Class<PaletteBitmap> {
        return PaletteBitmap::class.java
    }


    override fun get(): PaletteBitmap {
        return this
    }

    override fun getSize(): Int {
        return Util.getBitmapByteSize(bitmap)
    }

    override fun recycle() {

    }
}