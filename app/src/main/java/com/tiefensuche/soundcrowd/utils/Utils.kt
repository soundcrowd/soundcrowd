/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

internal object Utils {

    /**
     * Compute MD5 checksum for the input string
     */
    internal fun computeMD5(string: String): String {
        try {
            val messageDigest = MessageDigest.getInstance("MD5")
            val digestBytes = messageDigest.digest(string.toByteArray())
            return bytesToHexString(digestBytes)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException(e)
        }
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    internal fun getExtendedDescription(metadata: MediaMetadataCompat): MediaDescriptionCompat {
        val description = metadata.description
        val bob = MediaDescriptionCompat.Builder()
        bob.setDescription(description.description)
        bob.setIconBitmap(description.iconBitmap)
        bob.setIconUri(description.iconUri)
        bob.setMediaId(description.mediaId)
        bob.setMediaUri(description.mediaUri)
        bob.setTitle(description.title)
        bob.setSubtitle(description.subtitle)
        var bundle = description.extras
        if (bundle == null) {
            bundle = Bundle()
        }
        bundle.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
        bundle.putString(MediaMetadataCompatExt.METADATA_KEY_SOURCE, metadata.getString(MediaMetadataCompatExt.METADATA_KEY_SOURCE))

        if (metadata.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE) != null) {
            bundle.putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, metadata.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE))
        }

        bob.setExtras(bundle)
        return bob.build()
    }

    internal fun scaleBitmap(src: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val scaleFactor = (maxWidth.toDouble() / src.width).coerceAtMost(maxHeight.toDouble() / src.height)
        return Bitmap.createScaledBitmap(src,
                (src.width * scaleFactor).toInt(), (src.height * scaleFactor).toInt(), false)
    }

    /**
     * Get a color value from a theme attribute.
     *
     * @param context      used for getting the color.
     * @param attribute    theme attribute.
     * @param defaultColor default to use.
     * @return color value
     */
    internal fun getThemeColor(context: Context, attribute: Int, defaultColor: Int): Int {
        var themeColor = 0
        val packageName = context.packageName
        try {
            val packageContext = context.createPackageContext(packageName, 0)
            val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
            packageContext.setTheme(applicationInfo.theme)
            val theme = packageContext.theme
            val ta = theme.obtainStyledAttributes(intArrayOf(attribute))
            themeColor = ta.getColor(0, defaultColor)
            ta.recycle()
        } catch (e: PackageManager.NameNotFoundException) {
            // ignore
        }

        return themeColor
    }

    internal fun isAppInstalled(context: Context, uri: String): Boolean {
        val pm = context.packageManager
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES)
            return true
        } catch (ignored: PackageManager.NameNotFoundException) {
            // not installed
        }

        return false
    }
}