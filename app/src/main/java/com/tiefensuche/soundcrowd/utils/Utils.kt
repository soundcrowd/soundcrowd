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
import com.tiefensuche.soundcrowd.sources.MusicProviderSource
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal object Utils {

    @Throws(IOException::class)
    internal fun fetchFromUrl(url: String, post: String?): String {
        var reader: BufferedReader? = null
        try {
            val urlConnection = URL(url).openConnection() as? HttpURLConnection ?: throw IOException()
            if (post != null) {
                urlConnection.requestMethod = "POST"
                urlConnection.outputStream.write(post.toByteArray())
            }

            reader = if (urlConnection.responseCode < 400) {
                BufferedReader(InputStreamReader(
                        urlConnection.inputStream, "UTF-8"))
            } else {
                BufferedReader(InputStreamReader(
                        urlConnection.errorStream, "UTF-8"))
            }

            val sb = StringBuilder()
            var line: String?
            while (true) {
                line = reader.readLine()
                if (line == null) {
                    break
                }
                sb.append(line)
            }
            return sb.toString()
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (e: IOException) {
                    // ignore
                }

            }
        }
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
        bundle.putString("index", getIndexCharacter(description.title))
        bundle.putBoolean("stream", metadata.getString("stream") != null)
        bundle.putString(MusicProviderSource.CUSTOM_METADATA_MEDIA_SOURCE, metadata.getString(MusicProviderSource.CUSTOM_METADATA_MEDIA_SOURCE))

        if (metadata.getString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND) != null) {
            bundle.putString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND, metadata.getString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND))
        }


        bob.setExtras(bundle)
        return bob.build()
    }

    private fun getIndexCharacter(text: CharSequence?): String {
        if (text == null) {
            return "#"
        }
        val string = text.toString().toUpperCase()
        for (i in 0 until string.length) {
            if (Character.isLetter(string[i])) {
                return string.substring(i, i + 1)
            }
        }
        return "#"
    }

    internal fun scaleBitmap(src: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val scaleFactor = Math.min(
                maxWidth.toDouble() / src.width, maxHeight.toDouble() / src.height)
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
            e.printStackTrace()
        }

        return themeColor
    }

    internal fun isAppInstalled(context: Context, uri: String): Boolean {
        val pm = context.packageManager
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES)
            return true
        } catch (ignored: PackageManager.NameNotFoundException) {
        }

        return false
    }
}
