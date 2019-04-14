/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v7.graphics.Palette
import android.widget.ImageView
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.LetterTileDrawable
import com.tiefensuche.soundcrowd.utils.LogHelper

/**
 * Methods for loading artworks via [com.bumptech.glide.Glide] with support for [Palette]
 * and a default and placeholder artwork [LetterTileDrawable] containing the first letters of an artist
 */
internal object ArtworkHelper {
    const val MAX_ART_WIDTH = 800  // pixels
    const val MAX_ART_HEIGHT = 480  // pixels
    const val MAX_ART_WIDTH_ICON = 128  // pixels
    const val MAX_ART_HEIGHT_ICON = 128  // pixels
    private val TAG = LogHelper.makeLogTag(ArtworkHelper::class.java)

    internal fun loadArtwork(requests: GlideRequests, description: MediaDescriptionCompat, view: ImageView, listener: ColorsListener? = null) {
        if (description.iconBitmap != null) {
            view.setImageBitmap(description.iconBitmap)
            return
        }
        requests.clear(view)
        if (listener != null) {
            requests.`as`(PaletteBitmap::class.java).load(description).placeholder(getPlaceholder(view.context, description)).thumbnail(0.1f).apply(RequestOptions.centerCropTransform()).into(object : ImageViewTarget<PaletteBitmap>(view) {
                public override fun setResource(resource: PaletteBitmap?) {
                    if (resource != null) {
                        view.setImageBitmap(resource.bitmap)
                        val result = IntArray(2)
                        result[0] = resource.palette.getVibrantColor(ResourcesCompat.getColor(view.resources, R.color.colorPrimary, null))
                        val swatch = resource.palette.vibrantSwatch
                        if (swatch != null) {
                            result[1] = swatch.bodyTextColor
                        } else {
                            result[1] = Color.WHITE
                        }
                        listener.onColorsReady(result)
                    } else {
                        listener.onError()
                    }
                }
            })
        } else {
            requests.load(description).placeholder(getPlaceholder(view.context, description)).thumbnail(0.1f).apply(RequestOptions.centerCropTransform()).into(view)
        }
    }

    internal fun fetch(context: Context, description: MediaDescriptionCompat, width: Int, height: Int, listener: FetchListener) {
        GlideApp.with(context).asBitmap().load(description).apply(RequestOptions.centerCropTransform()).override(width, height).into(object : SimpleTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                listener.onFetched(resource)
            }
        })
    }

    private fun getPlaceholder(context: Context, description: MediaDescriptionCompat): LetterTileDrawable {
        val drawable = LetterTileDrawable(context)
        drawable.setTileDetails(description.title.toString(), description.title.toString())
        return drawable
    }

    internal interface FetchListener {
        fun onFetched(image: Bitmap)
    }

    internal interface ColorsListener {
        fun onColorsReady(colors: IntArray)

        fun onError()
    }
}