/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.support.v4.media.MediaMetadataCompat
import com.tiefensuche.soundcrowd.sources.LocalSource
import com.tiefensuche.soundcrowd.sources.MusicProviderSource
import com.tiefensuche.soundcrowd.utils.LogHelper
import com.tiefensuche.soundcrowd.waveform.CuePoint
import java.util.*

/**
 * Database that stores media items, metadata, and [CuePoint] items
 *
 * Created by tiefensuche on 23.09.16.
 */
internal class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    internal val cuePointItems: MutableList<MediaMetadataCompat>
        get() {
            val items = ArrayList<MediaMetadataCompat>()
            val mediaIds = ArrayList<String>()
            try {
                var cursor = readableDatabase.query(true, DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, arrayOf("media_id"), null, null, null, null, null, null)
                while (cursor.moveToNext()) {
                    mediaIds.add(cursor.getString(cursor.getColumnIndex("media_id")))
                }
                cursor.close()
                for (mediaId in mediaIds) {
                    cursor = readableDatabase.query(DATABASE_MEDIA_ITEMS_NAME, null, "id=?", arrayOf(mediaId), null, null, null, null)
                    if (cursor.moveToFirst()) {
                        items.add(buildItem(cursor, LocalSource::class.java.simpleName))
                    }
                }
                cursor.close()
            } catch (e: SQLException) {
                LogHelper.e(TAG, e, "error while querying cue points")
            }

            return items
        }

    init {
        writableDatabase.execSQL(DATABASE_MEDIA_ITEMS_CREATE)
        writableDatabase.execSQL(DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE)
        writableDatabase.execSQL(DATABASE_MEDIA_ITEMS_METADATA_CREATE)
        instance = this
    }

    private fun addMediaItem(item: MediaMetadataCompat) {
        val values = ContentValues()
        values.put("id", item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
        values.put("source", item.getString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE))
        values.put("artist", item.getString(MediaMetadataCompat.METADATA_KEY_ARTIST))
        values.put("album_art_url", item.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI))
        values.put("title", item.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        values.put("waveform_url", item.getString(MusicProviderSource.CUSTOM_METADATA_WAVEFORM_URL))
        values.put("duration", item.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
        try {
            writableDatabase.insertOrThrow(DATABASE_MEDIA_ITEMS_NAME, null, values)
        } catch (e: SQLException) {
            LogHelper.d(TAG, e, "value=", values.toString())
        }

    }

    internal fun getLastPosition(mediaId: String?): Long {
        var result: Long = 0
        if (mediaId == null) {
            return 0
        }
        try {
            val cursor = readableDatabase.query(DATABASE_MEDIA_ITEMS_METADATA_NAME, arrayOf("position"), "id=?", arrayOf(mediaId), null, null, null)
            if (cursor.moveToFirst()) {
                result = cursor.getLong(cursor.getColumnIndex("position"))
            }
            cursor.close()
        } catch (e: SQLException) {
            LogHelper.e(TAG, e, "error while query last position")
        }

        return result
    }

    internal fun updatePosition(mediaId: String, position: Int) {
        val values = ContentValues()
        values.put("id", mediaId)
        values.put("position", position)
        try {
            writableDatabase.insertOrThrow(DATABASE_MEDIA_ITEMS_METADATA_NAME, null, values)
        } catch (e: SQLException) {
            values.remove("id")
            try {
                writableDatabase.update(DATABASE_MEDIA_ITEMS_METADATA_NAME, values, "id=?", arrayOf(mediaId))
            } catch (e1: SQLiteException) {
                LogHelper.e(TAG, e1, "error while updating position")
            }

        }
    }

    private fun buildItem(cursor: Cursor, source: String, builder: MediaMetadataCompat.Builder = MediaMetadataCompat.Builder()): MediaMetadataCompat {
        return builder
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, cursor.getString(cursor.getColumnIndex("id")))
                .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, cursor.getString(cursor.getColumnIndex("source")))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, cursor.getString(cursor.getColumnIndex("artist")))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, cursor.getLong(cursor.getColumnIndex("duration")))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, cursor.getString(cursor.getColumnIndex("album_art_url")))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, cursor.getString(cursor.getColumnIndex("title")))
                .putString(MusicProviderSource.CUSTOM_METADATA_WAVEFORM_URL, cursor.getString(cursor.getColumnIndex("waveform_url")))
                .putString(MusicProviderSource.CUSTOM_METADATA_MEDIA_SOURCE, source)
                .putString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND, "track")
                .build()
    }

    internal fun addCuePoint(metadata: MediaMetadataCompat, position: Int) {
        addMediaItem(metadata)
        val values = ContentValues()
        values.put("media_id", metadata.description.mediaId)
        values.put("position", position)
        try {
            writableDatabase.insertOrThrow(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, null, values)
        } catch (e: SQLException) {
            LogHelper.e(TAG, e, "error while adding cue point")
        }

    }

    internal fun deleteCuePoint(mediaId: String, position: Int) {
        try {
            writableDatabase.delete(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, "media_id=? AND position=?", arrayOf(mediaId, Integer.toString(position)))
        } catch (e: SQLException) {
            LogHelper.e(TAG, e, "error while removing cue point")
        }

    }

    internal fun getCuePoints(mediaId: String?): Collection<CuePoint> {
        val result = ArrayList<CuePoint>()
        if (mediaId == null) {
            return result
        }
        try {
            val cursor = readableDatabase.query(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, arrayOf("media_id", "position", "description"), "media_id=?", arrayOf(mediaId), null, null, null)
            while (cursor.moveToNext()) {
                val cuePoint = CuePoint(cursor.getString(cursor.getColumnIndex("media_id")), cursor.getInt(cursor.getColumnIndex("position")), cursor.getString(cursor.getColumnIndex("description")))
                result.add(cuePoint)
            }
            cursor.close()
        } catch (e: SQLException) {
            LogHelper.e(TAG, e, "error while querying cue points")
        }

        return result
    }

    internal fun setDescription(mediaId: String, position: Int, text: String) {
        val values = ContentValues()
        values.put("description", text)
        try {
            writableDatabase.update(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, values, "media_id=? AND position=?", arrayOf(mediaId, Integer.toString(position)))
        } catch (e: SQLException) {
            LogHelper.e(TAG, e, "error while setting description")
        }

    }

    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_CREATE)
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE)
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_METADATA_CREATE)
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, i: Int, i1: Int) {
        // initial schema is up-to-date
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(DatabaseHelper::class.java)
        private const val DATABASE_NAME = "SoundCrowd"
        private const val DATABASE_VERSION = 1
        private const val DATABASE_MEDIA_ITEMS_NAME = "MediaItems"
        private const val DATABASE_MEDIA_ITEM_CUE_POINTS_NAME = "MediaItemCuePoints"
        private const val DATABASE_MEDIA_ITEMS_METADATA_NAME = "MediaItemsMetadata"

        private const val DATABASE_MEDIA_ITEMS_CREATE = "create table if not exists $DATABASE_MEDIA_ITEMS_NAME (id text primary key, artist text not null, title text not null, duration long not null, source text not null unique, download text unique, album_art_url text not null, waveform_url text);"
        private const val DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE = "create table if not exists $DATABASE_MEDIA_ITEM_CUE_POINTS_NAME (media_id text not null, position int not null, description text, CONSTRAINT pk_media_item_star PRIMARY KEY (media_id,position));"
        private const val DATABASE_MEDIA_ITEMS_METADATA_CREATE = "create table if not exists $DATABASE_MEDIA_ITEMS_METADATA_NAME (id text primary key, position long, album_art_url text, vibrant_color int, text_color int)"

        lateinit var instance: DatabaseHelper
    }
}
