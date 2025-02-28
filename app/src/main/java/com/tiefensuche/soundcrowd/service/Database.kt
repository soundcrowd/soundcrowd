/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.service

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.util.Log
import androidx.core.database.getStringOrNull
import androidx.media3.common.MediaItem
import com.tiefensuche.soundcrowd.plugins.MediaItemUtils
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.waveform.CuePoint

/**
 * Database that stores media items, metadata, and [CuePoint] items
 *
 * Created by tiefensuche on 23.09.16.
 */
internal class Database(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "SoundCrowd"
        private const val DATABASE_VERSION = 3

        const val ARTIST = "artist"
        const val TITLE = "title"
        const val ID = "id"
        const val DURATION = "duration"
        const val SOURCE = "source"
        const val DOWNLOAD = "download"
        const val ALBUM_ART_URL = "album_art_url"
        const val WAVEFORM_URL = "waveform_url"
        const val PLUGIN = "plugin"

        const val DATABASE_MEDIA_ITEMS_NAME = "MediaItems"

        private const val DATABASE_MEDIA_ITEMS_CREATE = "create table if not exists $DATABASE_MEDIA_ITEMS_NAME ($ID text primary key, $ARTIST text not null, $TITLE text not null, $DURATION long not null, $SOURCE text not null unique, $DOWNLOAD text unique, $ALBUM_ART_URL text not null, $WAVEFORM_URL text, $PLUGIN text);"

        private var TAG = Database::class.simpleName

        private const val MEDIA_ID = "media_id"
        private const val POSITION = "position"
        private const val DESCRIPTION = "description"

        private const val DATABASE_MEDIA_ITEM_CUE_POINTS_NAME = "MediaItemStars"
        private const val DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE = "create table if not exists $DATABASE_MEDIA_ITEM_CUE_POINTS_NAME ($MEDIA_ID text not null, $POSITION int not null, $DESCRIPTION text, CONSTRAINT pk_media_item_star PRIMARY KEY ($MEDIA_ID,$POSITION))"

        private const val DATABASE_MEDIA_ITEMS_METADATA_NAME = "MediaItemsMetadata"
        private const val DATABASE_MEDIA_ITEMS_METADATA_CREATE = "create table if not exists $DATABASE_MEDIA_ITEMS_METADATA_NAME ($ID text primary key, $POSITION long, $ALBUM_ART_URL text, vibrant_color int, text_color int)"
    }

    private fun addMediaItem(item: MediaItem) {
        try {
            val values = ContentValues()
            val mediaId = item.mediaId
            values.put(ID, mediaId.substring(mediaId.indexOf('|') + 1))
            values.put(SOURCE, item.requestMetadata.mediaUri.toString())
            values.put(ARTIST, item.mediaMetadata.artist.toString())
            values.put(ALBUM_ART_URL, item.mediaMetadata.artworkUri.toString())
            values.put(TITLE, item.mediaMetadata.title.toString())
            values.put(WAVEFORM_URL, item.mediaMetadata.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_WAVEFORM_URL))
            values.put(DURATION, item.mediaMetadata.durationMs)
            values.put(PLUGIN, item.mediaMetadata.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_PLUGIN))
            writableDatabase.insertOrThrow(DATABASE_MEDIA_ITEMS_NAME, null, values)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getMediaItem(mediaId: String): MediaItem? {
        val cursor = readableDatabase.query(DATABASE_MEDIA_ITEMS_NAME, null, "$ID=?", arrayOf(mediaId), null, null, null, null)
        if (!cursor.moveToFirst()) {
            return null
        }
        return buildItem(cursor)
    }

    private fun buildItem(cursor: Cursor): MediaItem {
        return MediaItemUtils.createMediaItem(
            cursor.getString(cursor.getColumnIndexOrThrow(ID)),
            Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(SOURCE))),
            cursor.getString(cursor.getColumnIndexOrThrow(TITLE)),
            cursor.getLong(cursor.getColumnIndexOrThrow(DURATION)),
            cursor.getString(cursor.getColumnIndexOrThrow(ARTIST)),
            null,
            Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(ALBUM_ART_URL))),
            cursor.getString(cursor.getColumnIndexOrThrow(WAVEFORM_URL)),
            plugin = cursor.getString(cursor.getColumnIndexOrThrow(PLUGIN))
        )
    }

    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_CREATE)
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE)
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_METADATA_CREATE)
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> {
                sqLiteDatabase.execSQL("ALTER TABLE $DATABASE_MEDIA_ITEMS_METADATA_NAME ADD COLUMN vibrant_color int")
                sqLiteDatabase.execSQL("ALTER TABLE $DATABASE_MEDIA_ITEMS_METADATA_NAME ADD COLUMN text_color int")
            }
            2 -> {
                sqLiteDatabase.execSQL("ALTER TABLE $DATABASE_MEDIA_ITEMS_NAME ADD COLUMN $PLUGIN text")
            }
        }
    }

    internal fun getCuePointItems(): ArrayList<MediaItem> {
            val items = ArrayList<MediaItem>()
            try {
                val cursor = readableDatabase.query(DATABASE_MEDIA_ITEMS_NAME,
                        null, "EXISTS (SELECT $MEDIA_ID FROM $DATABASE_MEDIA_ITEM_CUE_POINTS_NAME WHERE ${DATABASE_MEDIA_ITEM_CUE_POINTS_NAME}.$MEDIA_ID = ${DATABASE_MEDIA_ITEMS_NAME}.$ID)", null, null, null, null, null)
                while (cursor.moveToNext()) {
                    items.add(buildItem(cursor))
                }
                cursor.close()
            } catch (e: SQLException) {
                Log.e(TAG, "error while querying cue points", e)
            }
            return items
        }

    internal fun getLastPosition(mediaId: String?): Long {
        var result: Long = 0
        if (mediaId == null)
            return 0
        try {
            val cursor = readableDatabase.query(DATABASE_MEDIA_ITEMS_METADATA_NAME,
                    arrayOf(POSITION), "$ID=?", arrayOf(mediaId),
                    null, null, null)
            if (cursor.moveToFirst()) {
                result = cursor.getLong(cursor.getColumnIndexOrThrow(POSITION))
            }
            cursor.close()
        } catch (e: SQLException) {
            Log.e(TAG, "error while query last position", e)
        }

        return result
    }

    internal fun updatePosition(metadata: MediaItem, position: Long) {
        addMediaItem(metadata)
        metadata.mediaId.let {
            val values = ContentValues()
            values.put(ID, it)
            values.put(POSITION, position)
            try {
                writableDatabase.insertOrThrow(DATABASE_MEDIA_ITEMS_METADATA_NAME, null, values)
            } catch (e: SQLException) {
                values.remove(ID)
                try {
                    writableDatabase.update(DATABASE_MEDIA_ITEMS_METADATA_NAME, values, "$ID=?", arrayOf(it))
                } catch (e1: SQLiteException) {
                    Log.e(TAG, "error while updating position", e1)
                }
            }
        }
    }

    internal fun addCuePoint(metadata: MediaItem, position: Int, description: String?) {
        addMediaItem(metadata)
        val values = ContentValues()
        values.put(MEDIA_ID, metadata.mediaId)
        values.put(POSITION, position)
        values.put(DESCRIPTION, description)
        try {
            writableDatabase.insertOrThrow(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, null, values)
        } catch (e: SQLException) {
            Log.e(TAG, "error while adding cue point", e)
        }
    }

    internal fun deleteCuePoint(mediaId: String, position: Int) {
        try {
            writableDatabase.delete(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME,
                    "$MEDIA_ID=? AND $POSITION=?", arrayOf(mediaId, position.toString()))
        } catch (e: SQLException) {
            Log.e(TAG, "error while removing cue point", e)
        }
    }

    internal fun getCuePoints(mediaId: String): Collection<CuePoint> {
        val result = ArrayList<CuePoint>()
        try {
            val cursor = readableDatabase.query(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME,
                    null, "$MEDIA_ID=?", arrayOf(mediaId),
                    null, null, null)
            while (cursor.moveToNext()) {
                result.add(CuePoint(cursor.getString(cursor.getColumnIndexOrThrow(MEDIA_ID)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(POSITION)),
                    cursor.getStringOrNull(cursor.getColumnIndexOrThrow(DESCRIPTION)).toString()
                ))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "error while querying cue points", e)
        }

        return result
    }

    internal fun setDescription(mediaId: String, position: Int, text: String?) {
        val values = ContentValues()
        values.put(DESCRIPTION, text)
        try {
            writableDatabase.update(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, values,
                    "$MEDIA_ID=? AND $POSITION=?", arrayOf(mediaId, position.toString()))
        } catch (e: SQLException) {
            Log.e(TAG, "error while setting description", e)
        }
    }
}