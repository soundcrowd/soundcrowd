/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.sources

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import com.tiefensuche.soundcrowd.plugins.MediaItemUtils
import java.util.*

/**
 * Source plugin that provides local media items from the device
 *
 * Created by tiefensuche on 02.04.2016.
 */
internal class LocalSource(private val context: Context) {

    private val tracks = ArrayList<MediaItem>()

    internal fun resolve(uri: Uri): MediaItem {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "artist"
        val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "album"
        val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "title"
        val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        mmr.release()

        return MediaItemUtils.createMediaItem(uri.toString().hashCode().toString(), uri, title, duration, artist, album, uri, uri.toString(), plugin = NAME)
    }

    @Throws(Exception::class)
    private fun loadMusic() {

        // check for read storage permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Need permission to read audio files")
            return
        }

        val res = context.contentResolver
        val musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0"
        val musicCursor = res.query(musicUri, null, selection, null, null)

        if (musicCursor != null && musicCursor.moveToFirst()) {
            val titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durationColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

            do {
                try {
                    val id = musicCursor.getLong(idColumn)
                    val title = musicCursor.getString(titleColumn)
                    val artist = musicCursor.getString(artistColumn)
                    val album = musicCursor.getString(albumColumn)
                    val duration = musicCursor.getLong(durationColumn)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    tracks.add(MediaItemUtils.createMediaItem(id.toString(), uri, title, duration, artist, album, uri, uri.toString(), plugin = NAME))
                } catch (e: Exception) {
                    Log.w(TAG, "error while processing track", e)
                }
            } while (musicCursor.moveToNext())
        }

        musicCursor?.close()
    }

    @Throws(Exception::class)
    internal fun tracks(): ArrayList<MediaItem> {
        if (tracks.isEmpty()) {
            loadMusic()
        }
        return tracks
    }

    companion object {
        const val NAME = "Local"
        private val TAG = LocalSource::class.simpleName
    }
}