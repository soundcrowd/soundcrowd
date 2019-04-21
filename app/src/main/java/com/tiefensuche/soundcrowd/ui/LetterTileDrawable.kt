/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.*
import android.graphics.Paint.Align
import android.graphics.drawable.Drawable
import android.text.TextUtils
import com.tiefensuche.soundcrowd.R

/**
 * A drawable that encapsulates all the functionality needed to display a letter tile to
 * represent a artist/album/playlist image.
 */
class LetterTileDrawable(context: Context) : Drawable() {
    private val TAG = LetterTileDrawable::class.java.simpleName
    private val mPaint: Paint = Paint()
    private var mDisplayName: String? = null

    private var mIdentifier: String? = null
    private var mScale = 1.0f
    private var mOffset = 0.0f
    private var mIsCircle = false

    val color: Int
        get() = pickColor(mIdentifier)

    init {
        mPaint.isFilterBitmap = true
        mPaint.isDither = true

        initializeStaticVariables(context.resources)
    }

    override fun draw(canvas: Canvas) {
        //        setBounds(0, 0, 120, 120);
        val bounds = bounds
        if (!isVisible || bounds.isEmpty) {
            return
        }
        // Draw letter tile.
        drawLetterTile(canvas)
    }

    private fun drawLetterTile(canvas: Canvas) {
        // Draw background color.
        sPaint.color = pickColor(mIdentifier)

        sPaint.alpha = mPaint.alpha
        val bounds = bounds
        val minDimension = Math.min(bounds.width(), bounds.height())

        if (mIsCircle) {
            canvas.drawCircle(bounds.centerX().toFloat(), bounds.centerY().toFloat(), (minDimension / 2).toFloat(), sPaint)
        } else {
            canvas.drawRect(bounds, sPaint)
        }

        // Draw letter/digit only if the first character is an english letter
        mDisplayName?.let {
            if (it.isEmpty() || !isEnglishLetter(it[0])) {
                return
            }
            var numChars = 1

            // Draw letter or digit.
            sChars[0] = Character.toUpperCase(it[0])

            if (it.length > 1 && isEnglishLetter(it[1])) {
                sChars[1] = Character.toLowerCase(it[1])
                numChars = 2
            }

            // Scale text by canvas bounds and user selected scaling factor
            sPaint.textSize = mScale * sLetterToTileRatio * minDimension.toFloat()
            //sPaint.setTextSize(sTileLetterFontSize);
            sPaint.getTextBounds(sChars, 0, numChars, sRect)
            sPaint.color = sTileFontColor

            // Draw the letter in the canvas, vertically shifted up or down by the user-defined
            // offset
            canvas.drawText(sChars, 0, numChars, bounds.centerX().toFloat(),
                    bounds.centerY().toFloat() + mOffset * bounds.height() + (sRect.height() / 2).toFloat(),
                    sPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        mPaint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        mPaint.colorFilter = cf
    }

    override fun getOpacity(): Int {
        return android.graphics.PixelFormat.OPAQUE
    }

    /**
     * Scale the drawn letter tile to a ratio of its default size
     *
     * @param scale The ratio the letter tile should be scaled to as a percentage of its default
     * size, from a scale of 0 to 2.0f. The default is 1.0f.
     */
    fun setScale(scale: Float) {
        mScale = scale
    }

    /**
     * Assigns the vertical offset of the position of the letter tile to the ContactDrawable
     *
     * @param offset The provided offset must be within the range of -0.5f to 0.5f.
     * If set to -0.5f, the letter will be shifted upwards by 0.5 times the height of the canvas
     * it is being drawn on, which means it will be drawn with the center of the letter starting
     * at the top edge of the canvas.
     * If set to 0.5f, the letter will be shifted downwards by 0.5 times the height of the canvas
     * it is being drawn on, which means it will be drawn with the center of the letter starting
     * at the bottom edge of the canvas.
     * The default is 0.0f.
     */
    fun setOffset(offset: Float) {
        mOffset = offset
    }

    /**
     * Sets the tile data used to determine the display text and color
     *
     * @param displayName the name to display - Some logic will be applied to do some trimming
     * and up to the first two letters will be displayed
     * @param identifier  the identifier used to determine the color of the background.  For
     * album, use albumId, for artist use artistName and for playlist use
     * playlistId
     */
    fun setTileDetails(displayName: String, identifier: String) {
        mDisplayName = getTrimmedName(displayName)
        mIdentifier = getTrimmedName(identifier)
        invalidateSelf()
    }

    fun setIsCircular(isCircle: Boolean) {
        mIsCircle = isCircle
    }

    companion object {

        /**
         * Reusable components to avoid new allocations
         */
        private val sPaint = Paint()
        private val sRect = Rect()
        private val sChars = CharArray(2)
        /**
         * Letter tile
         */
        private var sColors: TypedArray? = null
        private lateinit var sVibrantDarkColors: TypedArray
        private var sDefaultColor: Int = 0
        private var sTileFontColor: Int = 0
        private var sLetterToTileRatio: Float = 0.toFloat()

        @Synchronized
        private fun initializeStaticVariables(res: Resources) {
            if (sColors == null) {
                sColors = res.obtainTypedArray(R.array.letter_tile_colors)
                sVibrantDarkColors = res.obtainTypedArray(R.array.letter_tile_vibrant_dark_colors)
                sDefaultColor = Color.GRAY //res.getColor(R.color.letter_tile_default_color);
                sTileFontColor = Color.WHITE //res.getColor(R.color.letter_tile_font_color);
                sLetterToTileRatio = res.getFraction(R.fraction.letter_to_tile_ratio, 1, 1)

                sPaint.typeface = Typeface.create(
                        res.getString(R.string.letter_tile_letter_font_family), Typeface.NORMAL)
                sPaint.textAlign = Align.CENTER
                sPaint.isAntiAlias = true
            }
        }

        /**
         * @return the corresponding index in the color palette based on the identifier
         */
        private fun getColorIndex(identifier: String?): Int {
            return if (TextUtils.isEmpty(identifier)) {
                -1
            } else sColors?.let { Math.abs(identifier.hashCode()) % it.length() } ?: -1
        }

        /**
         * Returns a deterministic color based on the provided contact identifier string.
         */
        private fun pickColor(identifier: String?): Int {
            val idx = getColorIndex(identifier)
            return if (idx == -1) {
                sDefaultColor
            } else sColors?.getColor(idx, sDefaultColor) ?: sDefaultColor

        }

        /**
         * Returns the vibrant matching color based on the provided contact identifier string.
         */
        private fun pickVibrantDarkColor(identifier: String): Int {
            val idx = getColorIndex(identifier)
            return if (idx == -1) {
                sDefaultColor
            } else sVibrantDarkColors.getColor(idx, sDefaultColor)

        }

        private fun isEnglishLetter(c: Char): Boolean {
            return 'A' <= c && c <= 'Z' || 'a' <= c && c <= 'z' || '0' <= c && c <= '9'
        }

        /**
         * A snippet is taken from MediaStore.Audio.keyFor method
         * This will take a name, removes things like "the", "an", etc
         * as well as special characters and return it
         *
         * @param name the string to trim
         * @return the trimmed name
         */

        private fun getTrimmedName(name: String?): String? {
            var name = name
            if (name == null || name.isEmpty()) {
                return name
            }

            name = name.trim { it <= ' ' }.toLowerCase()
            if (name.startsWith("the ")) {
                name = name.substring(4)
            }
            if (name.startsWith("an ")) {
                name = name.substring(3)
            }
            if (name.startsWith("a ")) {
                name = name.substring(2)
            }
            if (name.endsWith(", the") || name.endsWith(",the") ||
                    name.endsWith(", an") || name.endsWith(",an") ||
                    name.endsWith(", a") || name.endsWith(",a")) {
                name = name.substring(0, name.lastIndexOf(','))
            }
            name = name.replace("[\\[\\]()\"'.,?!]".toRegex(), "").trim { it <= ' ' }

            return name
        }
    }
}