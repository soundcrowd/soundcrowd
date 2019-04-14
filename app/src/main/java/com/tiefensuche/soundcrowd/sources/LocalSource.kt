/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.sources

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.utils.LogHelper
import java.util.*

/**
 * Source plugin that provides local media items from the device
 *
 * Created by tiefensuche on 02.04.2016.
 */
class LocalSource(private val context: MusicService) {
    private val tracks = ArrayList<MediaMetadataCompat>()

    fun resolve(uri: Uri): MediaMetadataCompat {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val duration = java.lang.Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
        mmr.release()

        return MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, Integer.toString(uri.toString().hashCode()))
                .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, uri.toString())
                .putString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND, "track")
                .putString(MusicProviderSource.CUSTOM_METADATA_WAVEFORM_URL, uri.toString())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, uri.toString())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MusicProviderSource.CUSTOM_METADATA_MEDIA_SOURCE, "LocalSource")
                .build()
    }

    private fun loadMusic() {

        // check for read storage permission
        if (ContextCompat.checkSelfPermission(context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            LogHelper.d(TAG, "Need permission WRITE_EXTERNAL_STORAGE")
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

                    val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    tracks.add(MediaMetadataCompat.Builder()
                            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, java.lang.Long.toString(id))
                            .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, trackUri.toString())
                            .putString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND, "track")
                            .putString(MusicProviderSource.CUSTOM_METADATA_WAVEFORM_URL, trackUri.toString())
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, if (artist == null || "<unknown>" == artist) title else artist)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, trackUri.toString())
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                            .putString(MusicProviderSource.CUSTOM_METADATA_MEDIA_SOURCE, "LocalSource")
                            .build())
                } catch (e: Exception) {
                    LogHelper.w(TAG, "error while processing track", e)
                    // on exception skip track
                }

            } while (musicCursor.moveToNext())
        }

        musicCursor?.close()
    }

    operator fun iterator(): Iterator<MediaMetadataCompat> {
        if (tracks.isEmpty()) {
            loadMusic()
        }
        return tracks.iterator()
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(LocalSource::class.java)
    }
}
