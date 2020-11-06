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
import com.tiefensuche.soundcrowd.ui.CueListFragment
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

        const val MEDIA_ID = "media_id"
        const val POSITION = "position"
        const val DESCRIPTION = "description"

        private const val DATABASE_MEDIA_ITEM_CUE_POINTS_NAME = "MediaItemStars"
        private const val DATABASE_MEDIA_ITEM_CUE_POINTS_TABLE = "$DATABASE_MEDIA_ITEM_CUE_POINTS_NAME stars inner join $DATABASE_MEDIA_ITEMS_NAME metadata on stars.$MEDIA_ID = metadata.$ID"
        private const val DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE = "create table if not exists $DATABASE_MEDIA_ITEM_CUE_POINTS_NAME ($MEDIA_ID text not null, $POSITION int not null, $DESCRIPTION text, CONSTRAINT pk_media_item_star PRIMARY KEY ($MEDIA_ID,$POSITION))"

        private const val DATABASE_MEDIA_ITEMS_METADATA_NAME = "MediaItemsMetadata"
        private const val DATABASE_MEDIA_ITEMS_METADATA_CREATE = "create table if not exists $DATABASE_MEDIA_ITEMS_METADATA_NAME ($ID text primary key, $POSITION long, $ALBUM_ART_URL text, vibrant_color int, text_color int)"

        lateinit var instance: Database
    }

    init {
        writableDatabase.execSQL(DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE)
        instance = this
    }

    internal val cuePointItems: ArrayList<MediaMetadataCompat>
        get() {
            val items = ArrayList<MediaMetadataCompat>()
            try {
                val cursor = readableDatabase.query(true, DATABASE_MEDIA_ITEM_CUE_POINTS_TABLE,
                        null, null, null, null, null, null, null)
                while (cursor.moveToNext()) {
                    items.add(buildItem(cursor, LocalSource::class.java.simpleName))
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
                result = cursor.getLong(cursor.getColumnIndex(POSITION))
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
            values.put(ID, musicId)
            values.put(POSITION, position)
            try {
                writableDatabase.insertOrThrow(DATABASE_MEDIA_ITEMS_METADATA_NAME, null, values)
            } catch (e: SQLException) {
                values.remove(ID)
                try {
                    writableDatabase.update(DATABASE_MEDIA_ITEMS_METADATA_NAME, values, "$ID=?", arrayOf(musicId))
                } catch (e1: SQLiteException) {
                    Log.e(TAG, "error while updating position", e1)
                }
            }
        }
    }

    private fun buildItem(cursor: Cursor, source: String, builder: MediaMetadataCompat.Builder = MediaMetadataCompat.Builder()): MediaMetadataCompat {
        return builder
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, cursor.getString(cursor.getColumnIndex(ID)))
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, cursor.getString(cursor.getColumnIndex(SOURCE)))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, cursor.getString(cursor.getColumnIndex(ARTIST)))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, cursor.getLong(cursor.getColumnIndex(DURATION)))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, cursor.getString(cursor.getColumnIndex(ALBUM_ART_URL)))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, cursor.getString(cursor.getColumnIndex(TITLE)))
                .putString(MediaMetadataCompatExt.METADATA_KEY_WAVEFORM_URL, cursor.getString(cursor.getColumnIndex(WAVEFORM_URL)))
                .putString(MediaMetadataCompatExt.METADATA_KEY_SOURCE, source)
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.MEDIA.name)
                .build()
    }

    internal fun addCuePoint(metadata: MediaMetadataCompat, position: Int, description: String?) {
        addMediaItem(metadata)
        val values = ContentValues()
        values.put(MEDIA_ID, metadata.description.mediaId)
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
                result.add(CuePoint(cursor.getString(cursor.getColumnIndex(MEDIA_ID)),
                        cursor.getInt(cursor.getColumnIndex(POSITION)),
                        cursor.getString(cursor.getColumnIndex(DESCRIPTION))))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "error while querying cue points", e)
        }

        return result
    }

    internal fun setDescription(mediaId: String, position: Int, text: String) {
        val values = ContentValues()
        values.put(DESCRIPTION, text)
        try {
            writableDatabase.update(DATABASE_MEDIA_ITEM_CUE_POINTS_NAME, values,
                    "$MEDIA_ID=? AND $POSITION=?", arrayOf(mediaId, position.toString()))
        } catch (e: SQLException) {
            Log.e(TAG, "error while setting description", e)
        }
    }

    internal fun getCueItems(): Collection<CueListFragment.CueItem> {
        val result = ArrayList<CueListFragment.CueItem>()
        try {
            val cursor = readableDatabase.query(DATABASE_MEDIA_ITEM_CUE_POINTS_TABLE,
                    arrayOf(MEDIA_ID, ARTIST, TITLE, POSITION, DESCRIPTION),
                    null, null, null, null, null)
            while (cursor.moveToNext()) {
                result.add(CueListFragment.CueItem(
                        CuePoint(cursor.getString(cursor.getColumnIndex(MEDIA_ID)),
                            cursor.getInt(cursor.getColumnIndex(POSITION)),
                            cursor.getString(cursor.getColumnIndex(DESCRIPTION))),
                        cursor.getString(cursor.getColumnIndex(ARTIST)),
                        cursor.getString(cursor.getColumnIndex(TITLE))))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "error while querying cue points", e)
        }

        return result
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