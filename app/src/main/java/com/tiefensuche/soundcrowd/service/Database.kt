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
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.extractMusicIDFromMediaID
import com.tiefensuche.soundcrowd.waveform.CuePoint

/**
 * Database that stores media items, metadata, and [CuePoint] items
 *
 * Created by tiefensuche on 23.09.16.
 */
internal class Database(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "SoundCrowd"
        private const val DATABASE_VERSION = 5

        const val ARTIST = "artist"
        const val TITLE = "title"
        const val ID = "id"
        const val DURATION = "duration"
        const val SOURCE = "source"
        const val DOWNLOAD = "download"
        const val ALBUM_ART_URL = "album_art_url"
        const val WAVEFORM_URL = "waveform_url"
        const val PLUGIN = "plugin"
        const val DATASOURCE = "datasource"

        const val DATABASE_MEDIA_ITEMS_NAME = "MediaItems"

        private const val DATABASE_MEDIA_ITEMS_CREATE = "create table if not exists $DATABASE_MEDIA_ITEMS_NAME ($ID text primary key, $ARTIST text not null, $TITLE text not null, $DURATION long not null, $SOURCE text not null unique, $DOWNLOAD text unique, $ALBUM_ART_URL text not null, $WAVEFORM_URL text, $PLUGIN text, $DATASOURCE boolean default 0);"

        private var TAG = Database::class.simpleName

        private const val MEDIA_ID = "media_id"
        private const val POSITION = "position"
        private const val DESCRIPTION = "description"
        private const val LAST_TIMESTAMP = "last"

        private const val DATABASE_MEDIA_ITEM_CUE_POINTS_NAME = "MediaItemStars"
        private const val DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE = "create table if not exists $DATABASE_MEDIA_ITEM_CUE_POINTS_NAME ($MEDIA_ID text not null, $POSITION int not null, $DESCRIPTION text, CONSTRAINT pk_media_item_star PRIMARY KEY ($MEDIA_ID,$POSITION))"

        private const val DATABASE_MEDIA_ITEMS_METADATA_NAME = "MediaItemsMetadata"
        private const val DATABASE_MEDIA_ITEMS_METADATA_CREATE = "create table if not exists $DATABASE_MEDIA_ITEMS_METADATA_NAME ($ID text primary key, $POSITION long, $ALBUM_ART_URL text, vibrant_color int, text_color int)"

        private const val DATABASE_PLAYLISTS_NAME = "Playlists"
        private const val DATABASE_PLAYLISTS_CREATE = "create table if not exists $DATABASE_PLAYLISTS_NAME ($ID integer primary key not null, name text, items text)"

        private const val DATABASE_SEARCH_HISTORY_NAME = "SearchHistory"
        private const val DATABASE_SEARCH_HISTORY_CREATE = "create table if not exists $DATABASE_SEARCH_HISTORY_NAME ($ID text primary key, $LAST_TIMESTAMP date default current_timestamp)"
    }

    private fun addMediaItem(item: MediaItem) {
        try {
            val values = ContentValues()
            val mediaId = item.mediaId
            values.put(ID, extractMusicIDFromMediaID(mediaId))
            values.put(ARTIST, item.mediaMetadata.artist.toString())
            values.put(TITLE, item.mediaMetadata.title.toString())
            values.put(DURATION, item.mediaMetadata.durationMs)
            values.put(SOURCE, item.requestMetadata.mediaUri.toString())
            values.put(ALBUM_ART_URL, if (item.mediaMetadata.artworkUri != null) item.mediaMetadata.artworkUri.toString() else null)
            values.put(WAVEFORM_URL, item.mediaMetadata.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_WAVEFORM_URL))
            values.put(PLUGIN, item.mediaMetadata.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_PLUGIN))
            values.put(DATASOURCE, item.mediaMetadata.extras?.getBoolean(MediaMetadataCompatExt.METADATA_KEY_DATASOURCE))
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
            cursor.getStringOrNull(cursor.getColumnIndexOrThrow(ARTIST)),
            null,
            cursor.getStringOrNull(cursor.getColumnIndexOrThrow(ALBUM_ART_URL))?.let { Uri.parse(it) },
            cursor.getStringOrNull(cursor.getColumnIndexOrThrow(WAVEFORM_URL)),
            plugin = cursor.getStringOrNull(cursor.getColumnIndexOrThrow(PLUGIN)),
            isDataSource = cursor.getInt(cursor.getColumnIndexOrThrow(DATASOURCE)) == 1
        )
    }

    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_CREATE)
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_CUE_POINTS_CREATE)
        sqLiteDatabase.execSQL(DATABASE_MEDIA_ITEMS_METADATA_CREATE)
        sqLiteDatabase.execSQL(DATABASE_PLAYLISTS_CREATE)
        sqLiteDatabase.execSQL(DATABASE_SEARCH_HISTORY_CREATE)
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            sqLiteDatabase.execSQL("ALTER TABLE $DATABASE_MEDIA_ITEMS_METADATA_NAME ADD COLUMN vibrant_color int")
            sqLiteDatabase.execSQL("ALTER TABLE $DATABASE_MEDIA_ITEMS_METADATA_NAME ADD COLUMN text_color int")
        }
        if (oldVersion < 3) {
            sqLiteDatabase.execSQL("ALTER TABLE $DATABASE_MEDIA_ITEMS_NAME ADD COLUMN $PLUGIN text")
        }
        if (oldVersion < 4) {
            // needed since 4.1.0
            sqLiteDatabase.execSQL(DATABASE_PLAYLISTS_CREATE)

            // needed since 4.0.0 but database upgrade was missing in release
            try {
                sqLiteDatabase.execSQL("ALTER TABLE $DATABASE_MEDIA_ITEMS_NAME ADD COLUMN $DATASOURCE boolean default 0")
            } catch (_: Exception) {}
        }
        if (oldVersion < 5) {
            sqLiteDatabase.execSQL(DATABASE_SEARCH_HISTORY_CREATE)

            // needed since 4.0.0 but database upgrade was missing in release
            try {
                sqLiteDatabase.execSQL("ALTER TABLE $DATABASE_MEDIA_ITEMS_NAME ADD COLUMN $DATASOURCE boolean default 0")
            } catch (_: Exception) {}
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
                result.add(
                    CuePoint(
                        cursor.getString(cursor.getColumnIndexOrThrow(MEDIA_ID)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(POSITION)),
                        cursor.getStringOrNull(cursor.getColumnIndexOrThrow(DESCRIPTION)) ?: ""
                    )
                )
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

    fun createPlaylist(name: String, metadata: MediaItem) {
        addMediaItem(metadata)
        val values = ContentValues()
        values.put("name", name)
        values.put("items", metadata.mediaId)
        writableDatabase.insertOrThrow(DATABASE_PLAYLISTS_NAME, null, values)
    }

    fun updatePlaylist(playlistId: String, tracks: List<MediaItem>) {
        tracks.forEach { addMediaItem(it) }
        val values = ContentValues()
        values.put(ID, playlistId)
        values.put("items", tracks.joinToString(",") { it.mediaId })
        try {
            writableDatabase.insertOrThrow(DATABASE_PLAYLISTS_NAME, null, values)
        } catch (e: SQLException) {
            values.remove(ID)
            writableDatabase.update(DATABASE_PLAYLISTS_NAME, values, "$ID=?", arrayOf(playlistId))
        }
    }

    fun addPlaylist(playlistId: String, metadata: MediaItem) {
        addMediaItem(metadata)
        val cursor = readableDatabase.query(DATABASE_PLAYLISTS_NAME,
            arrayOf("items"), "$ID=?", arrayOf(playlistId),
            null, null, null)
        if (cursor.moveToNext()) {
            val items = cursor.getString(cursor.getColumnIndex("items"))
            val values = ContentValues()
            values.put("items", if (items.isEmpty()) metadata.mediaId else "$items,${metadata.mediaId}")
            writableDatabase.update(DATABASE_PLAYLISTS_NAME, values, "$ID=?", arrayOf(playlistId))
        }
        cursor.close()
    }

    fun movePlaylist(playlistId: String, musicId: String, position: Int) {
        val cursor = readableDatabase.query(DATABASE_PLAYLISTS_NAME,
            arrayOf("items"), "$ID=?", arrayOf(playlistId),
            null, null, null)
        while (cursor.moveToNext()) {
            val items = cursor.getString(cursor.getColumnIndex("items")).split(",").toMutableList()
            items.remove(musicId)
            items.add(position, musicId)
            val values = ContentValues()
            values.put("items", items.joinToString(","))
            writableDatabase.update(DATABASE_PLAYLISTS_NAME, values, "$ID=?", arrayOf(playlistId))
        }
        cursor.close()
    }

    fun removePlaylist(playlistId: String, musicId: String) {
        val cursor = readableDatabase.query(DATABASE_PLAYLISTS_NAME,
            arrayOf("items"), "$ID=?", arrayOf(playlistId),
            null, null, null)
        while (cursor.moveToNext()) {
            val items = cursor.getString(cursor.getColumnIndex("items")).split(",").toMutableList()
            items.remove(musicId)
            val values = ContentValues()
            values.put("items", items.joinToString(","))
            writableDatabase.update(DATABASE_PLAYLISTS_NAME, values, "$ID=?", arrayOf(playlistId))
        }
        cursor.close()
    }

    fun getPlaylists(): List<MediaItem> {
        val result = ArrayList<MediaItem>()
        val cursor = readableDatabase.query(DATABASE_PLAYLISTS_NAME, arrayOf(ID, "name"), "name is not null", null, null, null, "name", null)
        while (cursor.moveToNext()) {
            result.add(MediaItemUtils.createBrowsableItem(cursor.getInt(cursor.getColumnIndex(ID)).toString(), cursor.getString(cursor.getColumnIndex("name")), MediaMetadataCompatExt.MediaType.COLLECTION))
        }
        cursor.close()
        return result
    }

    fun getPlaylist(playlistId: String): List<String> {
        val result = ArrayList<String>()
        val cursor = readableDatabase.query(DATABASE_PLAYLISTS_NAME, arrayOf("items"), "$ID=?", arrayOf(playlistId), null, null, null, null)
        if (cursor.moveToFirst()) {
            val items = cursor.getString(cursor.getColumnIndex("items")).split(",")
            items.forEach { result.add(it) }
        }
        cursor.close()
        return result
    }

    fun deletePlaylist(playlistId: String) {
        writableDatabase.delete(DATABASE_PLAYLISTS_NAME, "$ID=?", arrayOf(playlistId))
    }

    fun addSearchQuery(query: String) {
        val values = ContentValues()
        values.put(ID, query)
        try {
            writableDatabase.insertOrThrow(DATABASE_SEARCH_HISTORY_NAME, null, values)
        } catch (e: SQLException) {
            try {
                writableDatabase.execSQL("update $DATABASE_SEARCH_HISTORY_NAME set last=current_timestamp where $ID=?", arrayOf(query))
            } catch (e1: SQLiteException) {
                Log.e(TAG, "error while updating position", e1)
            }
        }
    }

    fun getRecentSearchQueries(query: String?): List<MediaItem> {
        val items = ArrayList<MediaItem>()
        try {
            val cursor = readableDatabase.query(DATABASE_SEARCH_HISTORY_NAME,
                null, "$ID like ?", arrayOf("%$query%"), null, null, "last desc", "50")
            while (cursor.moveToNext()) {
                items.add(MediaItemUtils.createTextItem(cursor.getString(cursor.getColumnIndex(ID)), true))
            }
            cursor.close()
        } catch (e: SQLException) {
            Log.e(TAG, "error while querying recent search queries", e)
        }
        return items
    }
}