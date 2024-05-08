/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.support.v4.media.MediaDescriptionCompat
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.palette.graphics.Palette
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.bumptech.glide.util.LruCache
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.LetterTileDrawable

/**
 * Methods for loading artworks via [com.bumptech.glide.Glide] with support for [Palette]
 * and a default and placeholder artwork [LetterTileDrawable] containing the first letters of an artist
 */
internal object ArtworkHelper {

    const val MAX_ART_WIDTH = 800  // pixels
    const val MAX_ART_HEIGHT = 480  // pixels
    const val MAX_ART_WIDTH_ICON = 128  // pixels
    const val MAX_ART_HEIGHT_ICON = 128  // pixels
    private val colorCache = LruCache<String, Palette>((10 * 1024).toLong())

    internal fun loadArtwork(requests: GlideRequests, description: MediaDescriptionCompat, view: ImageView, listener: ColorsListener? = null) {
        requests.clear(view)
        if (listener != null) {
            val placeholder = getPlaceholder(view.context, description)
            requests.asBitmap().load(description).placeholder(placeholder).apply(RequestOptions.centerCropTransform()).into(object : BitmapImageViewTarget(view) {
                public override fun setResource(resource: Bitmap?) {
                    if (resource == null)
                        return
                    description.mediaId?.let {
                        var palette = colorCache.get(it)
                        if (palette == null) {
                            palette = Palette.Builder(resource).generate()
                            colorCache.put(it, palette)
                        }
                        view.setImageBitmap(resource)
                        val result = IntArray(2)
                        result[0] = palette.getVibrantColor(ResourcesCompat.getColor(view.resources, R.color.colorPrimary, null))
                        val swatch = palette.vibrantSwatch
                        if (swatch != null) {
                            result[1] = swatch.bodyTextColor
                        } else {
                            result[1] = Color.WHITE
                        }
                        listener.onColorsReady(result)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    // in case the image can not be loaded, set the colors of the placeholder.
                    // the placeholder will remain as the image, so no need to set it again.
                    listener.onColorsReady(intArrayOf(placeholder.color, Color.WHITE))
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