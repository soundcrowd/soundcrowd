/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.waveform

import android.content.Context
import android.graphics.Bitmap

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory

/**
 * Created by tiefensuche on 6/29/17.
 */

internal class WaveformModelLoader private constructor(private val context: Context) : ModelLoader<StringKey, Bitmap> {

    override fun buildLoadData(model: StringKey, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap>? {
        return ModelLoader.LoadData(model, WaveformGeneratorGlideWrapper(context, model, width, height))
    }

    override fun handles(waveformParams: StringKey): Boolean {
        return true
    }

    internal class Factory(private val context: Context) : ModelLoaderFactory<StringKey, Bitmap> {

        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<StringKey, Bitmap> {
            return WaveformModelLoader(context)
        }

        override fun teardown() {
            // nothing
        }
    }
}