/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.sources

import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import com.tiefensuche.soundcrowd.database.DatabaseHelper
import com.tiefensuche.soundcrowd.plugins.PluginManager
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.utils.LogHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.CATEGORY_SEPARATOR
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.LEAF_SEPARATOR
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_MUSICS_CUE_POINTS
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_PLUGINS
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_ROOT
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_TEMP
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.createMediaID
import com.tiefensuche.soundcrowd.utils.Utils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.collections.ArrayList


/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
class MusicProvider(context: MusicService) {
    private val mMusicListById: ConcurrentMap<String, MutableMediaMetadata>
    private val musicByCategory: ConcurrentMap<String, MutableList<MediaMetadataCompat>>
    private val mFavoriteTracks: MutableSet<String>
    private val localSource: LocalSource
    private val pluginManager: PluginManager
    @Volatile
    private var mCurrentState = State.NON_INITIALIZED

    val allMusic: List<MediaMetadataCompat>?
        get() = getMusicByCategory(MEDIA_ID_ROOT)

    /**
     * Get an iterator over a shuffled collection of all songs
     */

    val shuffledMusic: Iterable<MediaMetadataCompat>
        get() {
            if (mCurrentState != State.INITIALIZED) {
                return emptyList()
            }
            val shuffled = ArrayList<MediaMetadataCompat>(mMusicListById.size)
            for (mutableMetadata in mMusicListById.values) {
                shuffled.add(mutableMetadata.metadata)
            }
            shuffled.shuffle()
            return shuffled
        }

    val isInitialized: Boolean
        get() = mCurrentState == State.INITIALIZED

    init {
        mMusicListById = ConcurrentHashMap()
        musicByCategory = ConcurrentHashMap()
        mFavoriteTracks = Collections.newSetFromMap(ConcurrentHashMap())
        localSource = LocalSource(context)
        pluginManager = PluginManager(context)
    }

    fun getMusicByCategory(category: String): List<MediaMetadataCompat> {
        return if (!musicByCategory.containsKey(category)) {
            emptyList()
        } else Collections.synchronizedList(musicByCategory[category])
    }

    /**
     * Return the MediaMetadataCompat for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */

    fun getMusic(musicId: String): MediaMetadataCompat? {
        return mMusicListById[musicId]?.metadata
    }

    fun resolveMusic(mediaId: String, callback: com.tiefensuche.soundcrowd.plugins.Callback<String>) {
        AsyncTask.execute(Runnable {
            val metadata = getMusic(MediaIDHelper.extractMusicIDFromMediaID(mediaId))
            if (!mediaId.contains(MEDIA_ID_ROOT)) {
                callback.onResult(metadata!!.getString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE))
                return@Runnable
            }
            for (plugin in pluginManager.plugins) {
                if (plugin.mediaCategories().contains(mediaId.split(CATEGORY_SEPARATOR)[1])) {
                    plugin.getMediaUrl(metadata!!.getString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE), callback)
                    return@Runnable
                }
            }

            // no plugin found, pass-though the source from metadata
            callback.onResult(metadata!!.getString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE))
        })
    }

    fun hasItems(mediaId: String, options: Bundle): Boolean =
            isInitialized && musicByCategory.containsKey(mediaId) && options.isEmpty

    /**
     * Get the list of music tracks from a server and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    fun retrieveMediaAsync(mediaId: String, extras: Bundle, callback: Callback) {
        // Asynchronously load the music catalog in a separate thread
        if (MEDIA_ID_ROOT == mediaId) {
            if (mCurrentState == State.INITIALIZED && OPTION_REFRESH == extras.getString(ACTION)) {
                mCurrentState = State.NON_INITIALIZED
            }
            if (mCurrentState == State.NON_INITIALIZED) {
                mCurrentState = State.INITIALIZING
            } else {
                return
            }
        }
        LogHelper.d(TAG, "retrieveMediaAsync mediaId=", mediaId)
        AsyncTask.execute { retrieveMedia(mediaId, extras.getString(ACTION), callback) }
    }

    private fun buildListsByArtist(local: List<MediaMetadataCompat>) {
        for (item in local) {
            addToCategory(MediaMetadataCompat.METADATA_KEY_ARTIST, MEDIA_ID_ROOT, item)
        }
    }

    private fun buildListsByAlbum(local: List<MediaMetadataCompat>) {
        for (item in local) {
            addToCategory(MediaMetadataCompat.METADATA_KEY_ALBUM, MEDIA_ID_ROOT, item)
        }
    }

    private fun addToCategory(key: String, category: String, item: MediaMetadataCompat) {
        if (item.getString(key) != null) {
            val value = item.getString(key).toLowerCase()
            var list: MutableList<MediaMetadataCompat>? = musicByCategory[category + CATEGORY_SEPARATOR + value]
            if (list == null) {
                list = ArrayList()
                musicByCategory[category + CATEGORY_SEPARATOR + value] = list
            }
            if (!exists(list, item)) {
                list.add(item)
            }
        }
    }

    private fun cleanCategories() {
        for (key in musicByCategory.keys) {
            if (musicByCategory[key]!!.size == 1) {
                if (!musicByCategory[MEDIA_ID_ROOT]!!.contains(musicByCategory[key]!![0])) {
                    musicByCategory[MEDIA_ID_ROOT]!!.add(musicByCategory[key]!![0])
                }
                musicByCategory.remove(key)
            }
        }
        test()
    }

    private fun test() {
        for (key in musicByCategory.keys) {
            LogHelper.d(TAG, "key=", key)
            val values = key.split(CATEGORY_SEPARATOR)
            if (values.size < 2) continue
            val builder = MediaMetadataCompat.Builder()
            builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, values[1])
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, values[1])
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "" + getMusicByCategory(key).size + " Tracks")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDurationSum(getMusicByCategory(key)))
                    .putString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND, "artist")
            musicByCategory[MEDIA_ID_ROOT]!!.add(builder.build())
        }
    }

    private fun exists(list: List<MediaMetadataCompat>, newItem: MediaMetadataCompat): Boolean {
        for (item in list) {
            if (item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID) == newItem.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)) {
                return true
            }
        }
        return false
    }

    private fun addMusic(tracks: Iterator<MediaMetadataCompat>?): ArrayList<MediaMetadataCompat> {
        val addedTracks = ArrayList<MediaMetadataCompat>()
        if (tracks == null) {
            return addedTracks
        }
        while (tracks.hasNext()) {
            val item = tracks.next()
            val musicId = item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
            val trackUrl = item.getString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE)
            val kind = item.getString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND)

            // skip tracks that have no music id or no track url
            if (musicId == null || "track" == kind && trackUrl == null || mMusicListById.containsKey(musicId)) {
                continue
            }

            mMusicListById[musicId] = MutableMediaMetadata(musicId, item)
            addedTracks.add(item)
        }
        return addedTracks
    }

    fun getChildren(mediaId: String, options: Bundle): List<MediaBrowserCompat.MediaItem> {
        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()

        if (!MediaIDHelper.isBrowseable(mediaId)) {
            return mediaItems
        }

        val list = getMusicByCategory(mediaId)
        val offset = options.getInt(OFFSET)
        if (list.size > offset) {
            for (metadata in list.subList(offset, list.size)) {
                if (isBrowsable(metadata)) {
                    mediaItems.add(createBrowsableMediaItemForGenre(metadata, mediaId, metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)))
                } else {
                    mediaItems.add(createMediaItem(metadata, mediaId))
                }
            }
        }

        return mediaItems
    }

    private fun isBrowsable(metadata: MediaMetadataCompat): Boolean {
        val kind = metadata.getString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND)
        for (browsableCategory in arrayOf("artist", "album", "user", "playlist", "plugin")) {
            if (browsableCategory == kind) {
                return true
            }
        }
        return false
    }

    private fun getDurationSum(tracks: List<MediaMetadataCompat>): Long {
        var sum: Long = 0
        for (metadata in tracks) {
            sum += metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        }
        return sum
    }


    private fun createBrowsableMediaItemForGenre(metadata: MediaMetadataCompat, type: String, key: String): MediaBrowserCompat.MediaItem {

        val builder = MediaMetadataCompat.Builder(metadata)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, createMediaID(null, type, key))

        return MediaBrowserCompat.MediaItem(Utils.getExtendedDescription(builder.build()),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }


    private fun createMediaItem(metadata: MediaMetadataCompat, path: String): MediaBrowserCompat.MediaItem {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)
        var value: String? = null
        if (MEDIA_ID_MUSICS_BY_ALBUM == path) {
            value = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)
        } else if (MEDIA_ID_MUSICS_BY_ARTIST == path) {
            value = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        }

        val hierarchyAwareMediaID: String
        hierarchyAwareMediaID = if (value == null) {
            createMediaID(
                    metadata.description.mediaId, path)
        } else {
            createMediaID(
                    metadata.description.mediaId, path, value)
        }

        val copy = MediaMetadataCompat.Builder(metadata)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                .build()
        return MediaBrowserCompat.MediaItem(Utils.getExtendedDescription(copy),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)

    }


    private fun searchMusic(query: String): MutableList<MediaMetadataCompat> {
        val result = ArrayList<MediaMetadataCompat>()
        val metadataFields = arrayOf(MediaMetadataCompat.METADATA_KEY_TITLE, MediaMetadataCompat.METADATA_KEY_ARTIST, MediaMetadataCompat.METADATA_KEY_ALBUM)
        for (categories in musicByCategory.keys) {
            for (track in getMusicByCategory(categories)) {
                if (result.contains(track)) continue
                for (metadataField in metadataFields) {
                    val value = track.getString(metadataField)
                    if (value != null && value.toLowerCase(Locale.US)
                                    .contains(query.toLowerCase(Locale.US))) {
                        result.add(track)
                        break
                    }
                }
            }
        }
        return result
    }

    private fun loadCuePoints() {
        val cuePoints = DatabaseHelper.instance.cuePointItems
        addMusic(cuePoints.iterator())
        musicByCategory[MEDIA_ID_MUSICS_CUE_POINTS] = cuePoints as MutableList<MediaMetadataCompat>
    }

    fun isFavorite(musicId: String): Boolean {
        return mFavoriteTracks.contains(musicId)
    }

    fun setFavorite(musicId: String, favorite: Boolean) {
        if (favorite) {
            mFavoriteTracks.add(musicId)
        } else {
            mFavoriteTracks.remove(musicId)
        }
    }

    @Synchronized
    fun updateMusicArt(musicId: String, albumArt: Bitmap, icon: Bitmap) {
        var metadata = getMusic(musicId)
        metadata = MediaMetadataCompat.Builder(metadata!!)

                // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                // example, on the lockscreen background when the media session is active.
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)

                // set small version of the album art in the DISPLAY_ICON. This is used on
                // the MediaDescription and thus it should be small to be serialized if
                // necessary
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)

                .build()

        val mutableMetadata = mMusicListById[musicId]
                ?: throw IllegalStateException("Unexpected error: Inconsistent data structures in " + "MusicProvider")

        mutableMetadata.metadata = metadata
    }

    fun addItem(mediaId: String, item: MediaMetadataCompat) {
        musicByCategory[mediaId]!!.add(item)
        addToCategory(MediaMetadataCompat.METADATA_KEY_ARTIST, MEDIA_ID_MUSICS_BY_ARTIST, item)
        addToCategory(MediaMetadataCompat.METADATA_KEY_ALBUM, MEDIA_ID_MUSICS_BY_ALBUM, item)
    }

    fun removeItem(mediaId: String, item: MediaMetadataCompat) {
        musicByCategory[mediaId]!!.remove(item)
    }

    private fun retrieveMedia(mediaId: String, action: String?, callback: Callback?) {
        // Local files
        if (mediaId.startsWith(MEDIA_ID_ROOT) && !mediaId.contains(MEDIA_ID_MUSICS_BY_SEARCH)) {
            val addedTracks = addMusic(localSource.iterator())
            musicByCategory[MEDIA_ID_ROOT] = ArrayList()
            buildListsByArtist(addedTracks)
            buildListsByAlbum(addedTracks)
            cleanCategories()
            loadCuePoints()
            mCurrentState = State.INITIALIZED
            callback?.onMusicCatalogReady(true)
        } else if (mediaId.startsWith(MEDIA_ID_ROOT + CATEGORY_SEPARATOR + MEDIA_ID_MUSICS_BY_SEARCH)) {
            // Search in local files
            val query = MediaIDHelper.getHierarchy(mediaId)[2]
            val results = searchMusic(query)
            musicByCategory[mediaId] = results
            callback?.onMusicCatalogReady(true)
        } else if (MEDIA_ID_MUSICS_CUE_POINTS == mediaId) { // Cue points
            loadCuePoints()
            callback?.onMusicCatalogReady(true)
        } else if (MEDIA_ID_PLUGINS == mediaId) {
            pluginManager.init()
            musicByCategory[MEDIA_ID_PLUGINS] = ArrayList()
            for (plugin in pluginManager.plugins) {
                for (category in plugin.mediaCategories()) {
                    val builder = MediaMetadataCompat.Builder()
                    builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, category)
                            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, category)
                            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, plugin.name())
                            .putString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND, "plugin")
                            .putString("stream", "true")
                            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, pluginManager.icons[plugin.name()])
                    musicByCategory[MEDIA_ID_PLUGINS]!!.add(builder.build())
                }
            }
            callback?.onMusicCatalogReady(true)
        } else if (mediaId.startsWith(MEDIA_ID_PLUGINS)) { // Plugins
            val category = MediaIDHelper.getHierarchy(mediaId)
            for (plugin in pluginManager.plugins) {
                if (plugin.mediaCategories().contains(category[1])) {
                    val res = object: com.tiefensuche.soundcrowd.plugins.Callback<JSONArray>{
                        override fun onResult(result: JSONArray) {
                            try {
                                addMediaFromJSON(mediaId, result)
                                callback?.onMusicCatalogReady(true)
                            } catch (e: JSONException) {
                                callback?.onMusicCatalogReady(false)
                                e.printStackTrace()
                            }
                        }
                    }
                    try {
                        when {
                            category.size == 2 -> // get category
                                plugin.getMediaItems(category[1], res)
                            category.size == 3 ->
                                plugin.getMediaItems(category[1], category[2], res)
                            category.size == 4 -> // search in category
                                plugin.getMediaItems(category[1], "", category[3], res)
                        }
                    } catch (e: Exception) {
                        callback?.onMusicCatalogReady(false)
                        e.printStackTrace()
                    }
                    break
                }
            }
        }
    }

    @Throws(JSONException::class)
    private fun addMediaFromJSON(mediaId: String, json: JSONArray) {
        var result = ArrayList<MediaMetadataCompat>()
        for (i in 0 until json.length()) {
            val `object` = json.getJSONObject(i)
            val metadata = fromJson(`object`)
            result.add(metadata)
        }

        result = addMusic(result.iterator())
        if (musicByCategory.containsKey(mediaId)) {
            musicByCategory[mediaId]!!.addAll(result)
        } else {
            musicByCategory[mediaId] = result
        }
    }

    @Throws(JSONException::class)
    private fun fromJson(json: JSONObject): MediaMetadataCompat {
        val builder = MediaMetadataCompat.Builder()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            when (value) {
                is String -> builder.putString(key, value)
                is Long -> builder.putLong(key, value)
                is Int -> builder.putLong(key, value.toLong())
            }
        }
        return builder.build()
    }

    fun resolve(uri: Uri): String {
        val track = localSource.resolve(uri)
        mMusicListById[track.description.mediaId!!] = MutableMediaMetadata(track.description.mediaId!!, track)
        musicByCategory[MEDIA_ID_TEMP] = mutableListOf(track)
        return MEDIA_ID_TEMP + LEAF_SEPARATOR + track.description.mediaId
    }

    enum class Source {
        LOCAL, CUE_POINTS, STREAM, LIKES, SEARCH, FOLLOWINGS, FOLLOWERS, PROFILE, PLAYLIST
    }

    enum class State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    interface Callback {
        fun onMusicCatalogReady(success: Boolean)
        fun onMusicCatalogReady(error: String)
    }

    companion object {

        const val ACTION = "ACTION"
        const val QUERY = "QUERY"
        private const val OFFSET = "OFFSET"
        const val OPTION_REFRESH = "REFRESH"
        private val TAG = LogHelper.makeLogTag(MusicProvider::class.java)
    }
}
