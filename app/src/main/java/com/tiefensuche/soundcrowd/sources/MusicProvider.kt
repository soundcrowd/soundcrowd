/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.sources

import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import com.tiefensuche.soundcrowd.database.Database
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.plugins.PluginManager
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.CUE_POINTS
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LAST_MEDIA
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LOCAL
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.TEMP
import com.tiefensuche.soundcrowd.utils.MediaIDHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.CATEGORY_SEPARATOR
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.LEAF_SEPARATOR
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.createMediaID
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.extractMusicIDFromMediaID
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.extractSourceValueFromMediaID
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.getHierarchy
import com.tiefensuche.soundcrowd.utils.Utils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
internal class MusicProvider(context: MusicService) {

    class Library {
        val root = BrowsableItem()
        val keys = HashMap<String, MutableMediaMetadata>()

        @Throws(Exception::class)
        fun getPath(request: Request, create: Boolean): BrowsableItem {
            var dir = root
            for (dirName in request.hierarchy) {
                dir.edges[dirName]?.let { dir = it } ?: let {
                    if (!create)
                        throw Exception("Directory $dirName does not exist!")
                    val item = BrowsableItem()
                    dir.edges[dirName] = item
                    dir = item
                }
            }

            return dir
        }
    }

    class BrowsableItem {
        var metadata: MediaMetadataCompat? = null
        val items = ArrayList<MediaMetadataCompat>()
        val edges = ConcurrentHashMap<String, BrowsableItem>()

        fun add(newItem: MediaMetadataCompat) {
            var exists = false
            for (item in items) {
                if (item.description.mediaId == newItem.description.mediaId) {
                    exists = true
                    break
                }
            }
            if (!exists)
                items.add(newItem)
        }
    }

    private val library = Library()
    private val localSource: LocalSource = LocalSource(context)
    private val pluginManager: PluginManager = PluginManager(context)

    inner class Request {

        val mediaId: String
        lateinit var hierarchy: Array<String>
        lateinit var source: String
        lateinit var category: String
        var path: String? = null
        var query: String? = null
        lateinit var options: Bundle

        constructor(mediaId: String) {
            this.mediaId = mediaId
            fromMediaId(mediaId)
        }

        constructor(bundle: Bundle) {
            mediaId = bundle.getString(MEDIA_ID) ?: throw Exception("missing MEDIA_ID")
            fromMediaId(mediaId)
            options = bundle
        }

        private fun parseQuery(cmd: String, query: String): Boolean {
            if (cmd != QUERY)
                return false
            this.query = query
            return true
        }

        private fun fromMediaId(mediaId: String) {
            hierarchy = getHierarchy(mediaId)
            for (i in hierarchy.indices) {
                if (i < hierarchy.size - 1 && parseQuery(hierarchy[i], hierarchy[i + 1])) {
                    if (i + 2 == hierarchy.size - 1 && path == null) {
                        path = hierarchy[i + 2]
                    } else if (i + 1 != hierarchy.size - 1)
                        throw Exception("Extra arguments after query!")
                    break
                }
                when (i) {
                    0 -> source = hierarchy[i]
                    1 -> {
                        if (source == LOCAL)
                            path = hierarchy[i]
                        else
                            category = hierarchy[i]
                    }
                    2 -> {
                        if (source == LOCAL)
                            throw Exception("Extra arguments after path!")
                        path = hierarchy[i]
                    }
                    else -> throw Exception("Extra arguments")
                }
            }
        }
    }

    internal fun getMusicByCategory(mediaId: String): List<MediaMetadataCompat> {
        val dir = library.getPath(Request(mediaId), true)
        val res = ArrayList<MediaMetadataCompat>()
        for (item in dir.items)
            if (item.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE) == MediaMetadataCompatExt.MediaType.MEDIA.name)
                res.add(item)

        return res
    }

    /**
     * Return the MediaMetadataCompat for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */
    internal fun getMusic(musicId: String): MediaMetadataCompat? {
        return library.keys[musicId]?.metadata
    }

    internal fun resolveMusic(mediaId: String, callback: com.tiefensuche.soundcrowd.plugins.Callback<JSONObject>) {
        getMusic(extractMusicIDFromMediaID(mediaId))?.let {
            val json = MediaMetadataCompatExt.toJSON(it)

            for (item in listOf(LOCAL, CUE_POINTS, LAST_MEDIA, TEMP)) {
                if (mediaId.startsWith(item)) {
                    callback.onResult(json)
                    return
                }
            }

            pluginManager.plugins[extractSourceValueFromMediaID(mediaId)]?.let {
                AsyncTask.execute {
                    try {
                        it.getMediaUrl(json, callback)
                    } catch (e: Exception) {
                        Log.w(TAG, "failed to resolve music with plugin ${it.name()}", e)
                    }
                }
            } ?: Log.d(TAG, "no plugin found to resolve $mediaId")
        }
    }

    private fun addToCategory(dir: BrowsableItem, items: List<MediaMetadataCompat>) {
        val descriptions = HashMap<String, String>()
        for (item in items) {
            for (key in listOf(MediaMetadataCompat.METADATA_KEY_ARTIST, MediaMetadataCompat.METADATA_KEY_ALBUM)) {
                if (item.getString(key) != null) {
                    val value = MediaIDHelper.toBrowsableName(item.getString(key))
                    var node = dir.edges[value]
                    if (node == null) {
                        node = BrowsableItem()
                        dir.edges[value] = node
                        descriptions[value] = key
                    }
                    node.add(item)
                }
            }
        }
        buildRootCategory(dir, descriptions)
    }

    private fun buildRootCategory(dir: BrowsableItem, descriptions: Map<String, String>) {
        val root = BrowsableItem()

        // remove categories that contain only one single item and add the item for root
        for ((key, value) in dir.edges) {
            if (value.items.size == 1 && !root.items.contains(value.items[0])) {
                root.items.add(value.items[0])
            } else if (value.items.size > 1) {
                root.edges[key] = value
            }
        }

        // create browsable categories for root
        for ((key, value) in root.edges) {
            val description = value.items[0]
            val builder = MediaMetadataCompat.Builder()
            builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, key)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, description.getString(descriptions[key]))
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, (if (descriptions[key] == MediaMetadataCompat.METADATA_KEY_ARTIST) "Artist: " else "Album: ") +
                            getMusicByCategory(LOCAL + CATEGORY_SEPARATOR + key).size + " Tracks")
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, description.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, description.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDurationSum(getMusicByCategory(LOCAL + CATEGORY_SEPARATOR + key)))
                    .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.COLLECTION.name)
            val metadata = builder.build()
            value.metadata = metadata
            root.items.add(metadata)
        }
        library.root.edges[LOCAL] = root
    }

    private fun addMusic(tracks: Iterator<MediaMetadataCompat>): ArrayList<MediaMetadataCompat> {
        val addedTracks = ArrayList<MediaMetadataCompat>()

        while (tracks.hasNext()) {
            var item = tracks.next()

            var musicId = item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
            val url = item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)
            val type = item.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE)

            // skip tracks that have no track url and duplicates
            if (MediaMetadataCompatExt.MediaType.MEDIA.name == type && url == null) {
                Log.d(TAG, "skipping track with musicId=$musicId")
                continue
            }

            // when no music id exists, default to the md5 of the source
            if (musicId == null) {
                musicId = Utils.computeMD5(url)
            }

            // add the music id to the metadata
            item = MediaMetadataCompat.Builder(item).putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, musicId).build()

            library.keys[musicId] = MutableMediaMetadata(musicId, item)
            addedTracks.add(item)
        }

        return addedTracks
    }

    internal fun hasItems(request: Request): Boolean {
        if (request.options.get(OFFSET) != null || request.options.get(OPTION_REFRESH) != null)
            return false

        return try {
            library.getPath(request, false)
            true
        } catch (e: Exception) {
            false
        }
    }

    internal fun getChildren(request: Request): ArrayList<MediaBrowserCompat.MediaItem> {
        val result = ArrayList<MediaBrowserCompat.MediaItem>()
        val items = library.getPath(request, false).items
        val offset = request.options.getInt(OFFSET)
        if (items.size > offset) {
            for (metadata in items.subList(offset, items.size)) {
                if (metadata.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE) == MediaMetadataCompatExt.MediaType.COLLECTION.name ||
                        metadata.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE) == MediaMetadataCompatExt.MediaType.STREAM.name) {
                    result.add(createBrowsableMediaItem(metadata, metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)))
                } else {
                    result.add(createMediaItem(metadata, request.mediaId))
                }
            }
        }

        return result
    }

    private fun getDurationSum(tracks: List<MediaMetadataCompat>): Long {
        var sum: Long = 0
        for (metadata in tracks) {
            sum += metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        }
        return sum
    }

    private fun createBrowsableMediaItem(metadata: MediaMetadataCompat, key: String): MediaBrowserCompat.MediaItem {
        val builder = MediaMetadataCompat.Builder(metadata)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, key)

        return MediaBrowserCompat.MediaItem(Utils.getExtendedDescription(builder.build()),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createMediaItem(metadata: MediaMetadataCompat, path: String): MediaBrowserCompat.MediaItem {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)

        val hierarchyAwareMediaID = createMediaID(metadata.description.mediaId, path)
        val copy = MediaMetadataCompat.Builder(metadata)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                .build()
        return MediaBrowserCompat.MediaItem(Utils.getExtendedDescription(copy),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun searchMusic(dir: BrowsableItem, query: String): MutableList<MediaMetadataCompat> {
        val result = ArrayList<MediaMetadataCompat>()
        val metadataFields = arrayOf(MediaMetadataCompat.METADATA_KEY_TITLE,
                MediaMetadataCompat.METADATA_KEY_ARTIST, MediaMetadataCompat.METADATA_KEY_ALBUM)

        val dirs = ArrayList<List<MediaMetadataCompat>>()
        dirs.add(dir.items)
        for (list in dir.edges.values) {
            dirs.add(list.items)
        }

        for (list in dirs) {
            for (item in list) {
                if (item.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE) ==
                        MediaMetadataCompatExt.MediaType.COLLECTION.toString() || result.contains(item)) continue
                for (metadataField in metadataFields) {
                    val value = item.getString(metadataField)
                    if (value != null && value.toLowerCase(Locale.US)
                                    .contains(query.toLowerCase(Locale.US))) {
                        result.add(item)
                        break
                    }
                }
            }
        }
        return result
    }

    private fun loadCuePoints() {
        val cuePoints = MusicService.database.cuePointItems
        addMusic(cuePoints.iterator())
        val item = BrowsableItem()
        item.items.addAll(cuePoints)
        library.root.edges[CUE_POINTS] = item
    }

    internal fun loadLastMedia(musicId: String): Boolean {
        MusicService.database.getMediaItem(musicId)?.let {
            val track = MediaMetadataCompatExt.fromJson(it)
            track.description.mediaId?.let {
                library.keys[it] = MutableMediaMetadata(it, track)
                val request = Request(LAST_MEDIA + LEAF_SEPARATOR + it)
                val dir = library.getPath(request, true)
                dir.items.add(track)
                return true
            }
        }
        return false
    }

    @Synchronized
    internal fun updateMusicArt(musicId: String, albumArt: Bitmap, icon: Bitmap) {
        getMusic(musicId)?.let {
            val metadata = MediaMetadataCompat.Builder(it)

                    // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                    // example, on the lockscreen background when the media session is active.
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)

                    // set small version of the album art in the DISPLAY_ICON. This is used on
                    // the MediaDescription and thus it should be small to be serialized if
                    // necessary
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)

                    .build()

            val mutableMetadata = library.keys[musicId]
                    ?: throw IllegalStateException("Unexpected error: Inconsistent data structures in MusicProvider")

            mutableMetadata.metadata = metadata
        }
    }

    fun getPlugins(): ArrayList<Bundle> {
        if (!pluginManager.initialized)
            pluginManager.init()
        val plugins: ArrayList<Bundle> = ArrayList()
        for (plugin in pluginManager.plugins.values) {
            library.getPath(Request(plugin.name()), true)

            val bundle = Bundle()
            bundle.putString(PluginMetadata.NAME, plugin.name())
            bundle.putParcelable(PluginMetadata.ICON, plugin.getIcon())
            bundle.putString(PluginMetadata.PREFERENCES, plugin.preferences().toString())

            val categories = ArrayList<Bundle>()
            for (category in plugin.mediaCategories()) {
                val categoryBundle = Bundle()
                categoryBundle.putString(PluginMetadata.NAME, plugin.name() + CATEGORY_SEPARATOR + category)
                categoryBundle.putString(PluginMetadata.CATEGORY, category)
                categoryBundle.putString(PluginMetadata.MEDIA_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name)
                categories.add(categoryBundle)
            }

            bundle.putParcelableArrayList(PluginMetadata.CATEGORY, categories)
            plugins.add(bundle)
        }

        return plugins
    }

    /**
     * Get the list of music tracks from a server and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    internal fun retrieveMediaAsync(request: Request, callback: Callback<Any?>) {
        // Asynchronously load the music catalog in a separate thread
        AsyncTask.execute { retrieveMedia(request, callback) }
    }

    private fun retrieveMedia(request: Request, callback: Callback<Any?>?) {
        val dir = library.getPath(request, true)
        when (request.source) {
            LOCAL -> {
                try {
                    request.query?.let { query ->
                        library.root.edges[LOCAL]?.let {
                            dir.items.addAll(searchMusic(it, query))
                            callback?.onSuccess()
                        } ?: callback?.onError("Can not browse local media")
                    } ?: let {
                        addToCategory(dir, addMusic(localSource.iterator()))
                        loadCuePoints()
                        callback?.onSuccess()
                    }
                } catch (e: Exception) {
                    callback?.onError("error when loading local media", e)
                }
            }
            CUE_POINTS -> {
                loadCuePoints()
                callback?.onSuccess()
            }
            else -> {
                if (!library.root.edges.containsKey(request.source)) {
                    callback?.onError("unknown source ${request.source}")
                    return
                }
                val plugin = pluginManager.plugins[request.source] ?:let {
                    callback?.onError("no plugin for source ${request.source}")
                    return
                }
                val refresh = (request.options.getBoolean(OPTION_REFRESH))
                val res = object : com.tiefensuche.soundcrowd.plugins.Callback<JSONArray> {
                    override fun onResult(result: JSONArray) {
                        try {
                            addMediaFromJSON(plugin.name(), dir, request, result)
                            callback?.onSuccess()
                        } catch (e: JSONException) {
                            callback?.onError("Error when parsing json", e)
                        }
                    }
                }
                try {
                    request.query?.let {
                        plugin.getMediaItems(QUERY, request.path ?: "", it, res, refresh)
                    } ?: request.path?.let {
                        plugin.getMediaItems(request.category, it, res, refresh)
                    } ?: plugin.getMediaItems(request.category, res, refresh)
                } catch (e: Exception) {
                    callback?.onError("Error when requesting plugin: ${e.message}", e)
                }
            }
        }
    }

    @Throws(JSONException::class)
    private fun addMediaFromJSON(source: String, dir: BrowsableItem, request: Request, json: JSONArray) {
        var result = ArrayList<MediaMetadataCompat>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            obj.put(MediaMetadataCompatExt.METADATA_KEY_SOURCE, source)
            val metadata = MediaMetadataCompatExt.fromJson(obj)
            result.add(metadata)
        }
        result = addMusic(result.iterator())
        if (request.options.getBoolean(OPTION_REFRESH))
            dir.items.clear()

        for (item in result) {
            dir.add(item)
        }
    }

    internal fun resolve(uri: Uri): String? {
        val track = localSource.resolve(uri)
        track.description.mediaId?.let {
            library.keys[it] = MutableMediaMetadata(it, track)
            val request = Request(TEMP + LEAF_SEPARATOR + it)
            val dir = library.getPath(request, true)
            dir.items.add(track)
            return request.mediaId
        }
        return null
    }

    fun favorite(metadata: MediaMetadataCompat, callback: com.tiefensuche.soundcrowd.plugins.Callback<Boolean>) {
        pluginManager.plugins[metadata.getString(MediaMetadataCompatExt.METADATA_KEY_SOURCE)]?.favorite(
                metadata.getString(extractMusicIDFromMediaID(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)), callback)
                ?: Log.d(TAG, "No plugin to handle favorite request for ${metadata.getString(MediaMetadataCompatExt.METADATA_KEY_SOURCE)}")
    }

    fun addCuePoint(metadata: MediaMetadataCompat, extras: Bundle) {
        MusicService.database.addCuePoint(metadata,
                extras.getInt(Database.POSITION),
                extras.getString(Database.DESCRIPTION, ""))
        library.root.edges[CUE_POINTS]?.items?.add(metadata)
    }

    interface Callback<T> {
        fun onSuccess()
        fun onError(message: String, e: Exception? = null)
    }

    object Media {
        const val LOCAL = "LOCAL"
        const val CUE_POINTS = "CUE_POINTS"
        const val LAST_MEDIA = "LAST_MEDIA"
        const val TEMP = "TEMP"
    }

    object PluginMetadata {
        const val NAME = "NAME"
        const val CATEGORY = "CATEGORY"
        const val MEDIA_TYPE = "MEDIA_TYPE"
        const val PREFERENCES = "PREFERENCES"
        const val ICON = "ICON"
    }

    companion object {
        const val ACTION_GET_MEDIA = "GET_MEDIA"
        const val ACTION_GET_PLUGINS = "GET_PLUGINS"
        const val ACTION_ADD_CUE_POINT = "ACTION_ADD_CUE_POINT"
        const val MEDIA_ID = "MEDIA_ID"
        const val OFFSET = "OFFSET"
        const val QUERY = "QUERY"
        const val OPTION_REFRESH = "REFRESH"
        const val RESULT = "RESULT"
        private val TAG = MusicProvider::class.simpleName
    }
}