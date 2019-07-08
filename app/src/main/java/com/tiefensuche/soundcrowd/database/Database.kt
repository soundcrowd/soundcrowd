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
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.sources.LocalSource
import com.tiefensuche.soundcrowd.utils.MediaIDHelper
import com.tiefensuche.soundcrowd.waveform.CuePoint
import java.util.*

/**
 * Database that stores media items, metadata, and [CuePoint] items
 *
 * Created by tiefensuche on 23.09.16.
 */
internal class Database(context: Context) : MetadataDatabase(context) {

    companion object {
        private var TAG = Database::class.simpleName

        private const val DATABASE_MEDIA_ITEM_CUE_POINTS_NAME = "MediaItemStars"
        private const val DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE = "create table if not exists $DATABASE_MEDIA_ITEM_CUE_POINTS_NAME (media_id text not null, position int not null, description text, CONSTRAINT pk_media_item_star PRIMARY KEY (media_id,position));"

        private const val DATABASE_MEDIA_ITEMS_METADATA_NAME = "MediaItemsMetadata"
        private const val DATABASE_MEDIA_ITEMS_METADATA_CREATE = "create table if not exists $DATABASE_MEDIA_ITEMS_METADATA_NAME (id text primary key, position long, album_art_url text, vibrant_color int, text_color int)"

        lateinit var instance: Database
    }

    init {
        writableDatabase.execSQL(DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE)
        instance = this
    }

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
                Log.e(TAG, "error while querying cue points", e)
            }

            return items
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
            Log.e(TAG, "error while query last position", e)
        }

        return result
    }

    internal fun updatePosition(metadata: MediaMetadataCompat, position: Int) {
        addMediaItem(metadata)
        metadata.description.mediaId?.let {
            val musicId = MediaIDHelper.extractMusicIDFromMediaID(it)
            val values = ContentValues()
            values.put("id", musicId)
            values.put("position", position)
            try {
                writableDatabase.insertOrThrow(DATABASE_MEDIA_ITEMS_METADATA_NAME, null, values)
            } catch (e: SQLException) {
                values.remove("id")
                try {
                    writableDatabase.update(DATABASE_MEDIA_ITEMS_METADATA_NAME, values, "id=?", arrayOf(musicId))
                } catch (e1: SQLiteException) {
                    Log.e(TAG, "error while updating position", e1)
                }
            }
        }
    }

    private fun buildItem(cursor: Cursor, source: String, builder: MediaMetadataCompat.Builder = MediaMetadataCompat.Builder()): MediaMetadataCompat {
        return builder
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, cursor.getString(cursor.getColumnIndex("id")))
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, cursor.getString(cursor.getColumnIndex("source")))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, cursor.getString(cursor.getColumnIndex("artist")))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, cursor.getLong(cursor.getColumnIndex("duration")))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, cursor.getString(cursor.getColumnIndex("album_art_url")))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, cursor.getString(cursor.getColumnIndex("title")))
                .putString(MediaMetadataCompatExt.METADATA_KEY_WAVEFORM_URL, cursor.getString(cursor.getColumnIndex("waveform_url")))
                .putString(MediaMetadataCompatExt.METADATA_KEY_SOURCE, source)
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.MEDIA.name)
                .build()
    }

    internal fun addCuePoint(metadata: MediaMetadataCompat, position: Int, description: String?) {
        addMediaItem(metadata)
        val values = ContentValues()
        values.put("media_id", metadata.description.mediaId)
        values.put("position", position)
        values.put("description", description)
        try {
            writableDatabase.insertOrThrow(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, null, values)
        } catch (e: SQLException) {
            Log.e(TAG, "error while adding cue point", e)
        }
    }

    internal fun deleteCuePoint(mediaId: String, position: Int) {
        try {
            writableDatabase.delete(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, "media_id=? AND position=?", arrayOf(mediaId, position.toString()))
        } catch (e: SQLException) {
            Log.e(TAG, "error while removing cue point", e)
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
        } catch (e: Exception) {
            Log.e(TAG, "error while querying cue points", e)
        }

        return result
    }

    internal fun setDescription(mediaId: String, position: Int, text: String) {
        val values = ContentValues()
        values.put("description", text)
        try {
            writableDatabase.update(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, values, "media_id=? AND position=?", arrayOf(mediaId, position.toString()))
        } catch (e: SQLException) {
            Log.e(TAG, "error while setting description", e)
        }
    }

    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        super.onCreate(sqLiteDatabase)
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE)
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_METADATA_CREATE)
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, i: Int, i1: Int) {
        when (i) {
            1 -> {
                sqLiteDatabase.execSQL("ALTER TABLE $DATABASE_MEDIA_ITEMS_METADATA_NAME ADD COLUMN vibrant_color int")
                sqLiteDatabase.execSQL("ALTER TABLE $DATABASE_MEDIA_ITEMS_METADATA_NAME ADD COLUMN text_color int")
            }
        }
    }
}