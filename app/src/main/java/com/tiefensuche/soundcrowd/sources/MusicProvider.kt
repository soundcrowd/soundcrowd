/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.sources

import android.graphics.Bitmap
import android.media.MediaDataSource
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.plugins.IPlugin
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.service.PluginManager
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.CUE_POINTS
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LAST_MEDIA
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LOCAL
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.TEMP
import com.tiefensuche.soundcrowd.utils.MediaIDHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.CATEGORY_SEPARATOR
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.LEAF_SEPARATOR
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.createMediaID
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.extractMusicIDFromMediaID
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.getHierarchy
import com.tiefensuche.soundcrowd.utils.Utils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
internal class MusicProvider(context: MusicService) {


    class Library {
        val root = BrowsableItem(this)
        val keys = HashMap<String, MutableMediaMetadata>()

        @Throws(Exception::class)
        fun getPath(request: Request, create: Boolean): BrowsableItem {
            var dir = root
            for (dirName in request.hierarchy) {
                dir.edges[dirName]?.let { dir = it } ?: let {
                    if (!create)
                        throw Exception("Directory $dirName does not exist!")
                    val item = BrowsableItem(this)
                    dir.edges[dirName] = item
                    dir = item
                }
            }

            return dir
        }
    }

    class BrowsableItem(val library: Library) {
        val items = ArrayList<String>()
        val edges = ConcurrentHashMap<String, BrowsableItem>()
        val musicIds = HashSet<String>()

        fun items(): List<MediaMetadataCompat> {
            return items.mapNotNull { item -> library.keys[item]?.metadata }
        }

        fun add(newItem: MediaMetadataCompat) {
            if (!validate(newItem))
                return

            val musicId = newItem.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
            if (musicId == null || musicIds.contains(musicId))
                return

            if (!library.keys.containsKey(musicId)) {
                library.keys[musicId] = MutableMediaMetadata(musicId, newItem)
            }
            items.add(musicId)
            musicIds.add(musicId)
        }

        fun sort() {
            items.sortBy { item ->
                library.keys[item]?.metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                    ?.uppercase()
            }
        }

        fun clear() {
            items.clear()
            musicIds.clear()
        }

        private fun checkKeys(item: MediaMetadataCompat, keys: List<String>): Boolean {
            for (key in keys) {
                if (!item.containsKey(key))
                    return false
            }
            return true
        }

        private fun validate(item: MediaMetadataCompat): Boolean {
            if (!checkKeys(
                    item,
                    listOf(
                        MediaMetadataCompatExt.METADATA_KEY_TYPE,
                        MediaMetadataCompat.METADATA_KEY_ARTIST,
                        MediaMetadataCompat.METADATA_KEY_TITLE,
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI
                    )
                )
            )
                return false

            if (!MediaMetadataCompatExt.MediaType.values().map { it.name }
                    .contains(item.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE)))
                return false

            if (MediaMetadataCompatExt.MediaType.MEDIA.name != item.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE))
                return true

            if (!checkKeys(
                    item,
                    listOf(
                        MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
                        MediaMetadataCompat.METADATA_KEY_DURATION
                    )
                )
            )
                return false

            if (item.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) == 0L)
                return false

            return true
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
                        path = hierarchy[i]
                    }

                    else -> {
                        path += "/${hierarchy[i]}"
                    }
                }
            }
        }
    }

    internal fun getMusicByCategory(mediaId: String): List<MediaMetadataCompat> {
        val dir = library.getPath(Request(mediaId), true)
        val res = ArrayList<MediaMetadataCompat>()
        for (item in dir.items())
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

    internal fun updateExtendedMetadata(musicId: String) {
        getMusic(musicId)?.let {
            val jsonList = JSONArray()
            val cuePoints = MusicService.database.getCuePoints(musicId)
            for (cuePoint in cuePoints) {
                val json = JSONObject()
                    .put(Cues.POSITION, cuePoint.position)
                    .put(Cues.DESCRIPTION, cuePoint.description)
                jsonList.put(json)
            }

            library.keys[musicId]?.metadata = MediaMetadataCompat.Builder(it)
                .putLong(
                    Cues.LAST_POSITION,
                    MusicService.database.getLastPosition(musicId)
                )
                .putString(Cues.CUES, jsonList.toString()).build()
        }
    }

    internal fun resolveMusic(
        mediaId: String,
        callback: com.tiefensuche.soundcrowd.plugins.Callback<Pair<MediaMetadataCompat, MediaDataSource?>>
    ) {
        getMusic(extractMusicIDFromMediaID(mediaId))?.let { metadata ->
            val source = metadata.getString(MediaMetadataCompatExt.METADATA_KEY_SOURCE)
            if (source == LocalSource.NAME) {
                callback.onResult(Pair(metadata, null))
                return
            }

            PluginManager.plugins[source]?.let {
                ResolveTask(it, metadata, callback).execute()
            } ?: Log.d(TAG, "no plugin found to resolve $mediaId")
        }
    }

    class ResolveTask(
        val plugin: IPlugin,
        val metadata: MediaMetadataCompat,
        val callback: com.tiefensuche.soundcrowd.plugins.Callback<Pair<MediaMetadataCompat, MediaDataSource?>>
    ) : AsyncTask<MediaMetadataCompat, Void, Pair<MediaMetadataCompat, MediaDataSource?>>() {
        override fun doInBackground(vararg params: MediaMetadataCompat?): Pair<MediaMetadataCompat, MediaDataSource?>? {
            var res: Pair<MediaMetadataCompat, MediaDataSource?>? = null
            try {
                plugin.getMediaUrl(metadata, object :
                    com.tiefensuche.soundcrowd.plugins.Callback<Pair<MediaMetadataCompat, MediaDataSource?>> {
                    override fun onResult(result: Pair<MediaMetadataCompat, MediaDataSource?>) {
                        res = result
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "failed to resolve music with plugin ${plugin.name()}", e)
            }
            return res
        }

        override fun onPostExecute(result: Pair<MediaMetadataCompat, MediaDataSource?>?) {
            result?.let {
                callback.onResult(it)
            }
        }
    }

    private fun addToCategory(dir: BrowsableItem, items: ArrayList<MediaMetadataCompat>) {
        items.sortBy { metadata ->
            metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE).uppercase()
        }
        for (item in items) {
            dir.add(item)
            for (key in listOf(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                MediaMetadataCompat.METADATA_KEY_ALBUM
            )) {
                var category = dir.edges[key]
                if (category == null) {
                    category = BrowsableItem(library)
                    dir.edges[key] = category
                }
                val value = MediaIDHelper.toBrowsableName(item.getString(key))
                var node = category.edges[value]
                if (node == null) {
                    node = BrowsableItem(library)
                    category.edges[value] = node
                }
                node.add(item)
            }
        }
        buildRootCategory(dir)
    }

    private fun buildRootCategory(dir: BrowsableItem) {
        // create browsable categories for root
        for (category in listOf(
            MediaMetadataCompat.METADATA_KEY_ARTIST,
            MediaMetadataCompat.METADATA_KEY_ALBUM
        )) {
            dir.edges[category]?.let {
                for ((key, value) in it.edges) {
                    val description = value.items()[0]
                    val metadata = MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, key)
                        .putString(
                            MediaMetadataCompat.METADATA_KEY_TITLE,
                            description.getString(category)
                        )
                        .putString(
                            MediaMetadataCompat.METADATA_KEY_ARTIST,
                            getMusicByCategory(LOCAL + CATEGORY_SEPARATOR + category + CATEGORY_SEPARATOR + key).size.toString() + " Tracks"
                        )
                        .putBitmap(
                            MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                            description.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                        )
                        .putString(
                            MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                            description.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
                        )
                        .putLong(
                            MediaMetadataCompat.METADATA_KEY_DURATION,
                            getDurationSum(getMusicByCategory(LOCAL + CATEGORY_SEPARATOR + category + CATEGORY_SEPARATOR + key))
                        )
                        .putString(
                            MediaMetadataCompatExt.METADATA_KEY_TYPE,
                            MediaMetadataCompatExt.MediaType.COLLECTION.name
                        ).build()
                    it.add(metadata)
                }
                it.sort()
            }
        }
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
        val items = library.getPath(request, false).items()
        val offset = request.options.getInt(OFFSET)
        if (items.size > offset) {
            for (metadata in items.subList(offset, items.size)) {
                if (metadata.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE) == MediaMetadataCompatExt.MediaType.COLLECTION.name ||
                    metadata.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE) == MediaMetadataCompatExt.MediaType.STREAM.name
                ) {
                    result.add(
                        createBrowsableMediaItem(
                            metadata,
                            metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                        )
                    )
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

    private fun createBrowsableMediaItem(
        metadata: MediaMetadataCompat,
        key: String
    ): MediaBrowserCompat.MediaItem {
        val builder = MediaMetadataCompat.Builder(metadata)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, key)

        return MediaBrowserCompat.MediaItem(
            Utils.getExtendedDescription(builder.build()),
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }

    private fun createMediaItem(
        metadata: MediaMetadataCompat,
        path: String
    ): MediaBrowserCompat.MediaItem {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)

        val hierarchyAwareMediaID = createMediaID(metadata.description.mediaId, path)
        val copy = MediaMetadataCompat.Builder(metadata)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
            .build()
        return MediaBrowserCompat.MediaItem(
            Utils.getExtendedDescription(copy),
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
    }

    private fun searchMusic(dir: BrowsableItem, query: String): List<MediaMetadataCompat> {
        val result = ArrayList<MediaMetadataCompat>()
        val metadataFields = arrayOf(
            MediaMetadataCompat.METADATA_KEY_TITLE,
            MediaMetadataCompat.METADATA_KEY_ARTIST, MediaMetadataCompat.METADATA_KEY_ALBUM
        )

        for (item in dir.items()) {
            for (metadataField in metadataFields) {
                val value = item.getString(metadataField)
                if (value != null && value.lowercase(Locale.US)
                        .contains(query.lowercase(Locale.US))
                ) {
                    result.add(item)
                    break
                }
            }
        }
        return result
    }

    internal fun loadLastMedia(musicId: String): Boolean {
        MusicService.database.getMediaItem(musicId)?.let { track ->
            track.description.mediaId?.let {
                val request = Request(LAST_MEDIA + LEAF_SEPARATOR + it)
                val dir = library.getPath(request, true)
                dir.add(track)
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
        for (plugin in PluginManager.plugins.values) {
            library.getPath(Request(plugin.name()), true)

            val bundle = Bundle()
            bundle.putString(PluginMetadata.NAME, plugin.name())
            bundle.putParcelable(PluginMetadata.ICON, plugin.getIcon())

            val categories = ArrayList<Bundle>()
            for (category in plugin.mediaCategories()) {
                val categoryBundle = Bundle()
                categoryBundle.putString(
                    PluginMetadata.NAME,
                    plugin.name() + CATEGORY_SEPARATOR + category
                )
                categoryBundle.putString(PluginMetadata.CATEGORY, category)
                categoryBundle.putString(
                    PluginMetadata.MEDIA_TYPE,
                    MediaMetadataCompatExt.MediaType.STREAM.name
                )
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
    internal fun retrieveMediaAsync(request: Request, callback: Callback) {
        // Asynchronously load the music catalog in a separate thread
        AsyncTask.execute { retrieveMedia(request, callback) }
    }

    private fun retrieveMedia(request: Request, callback: Callback?) {
        val dir = library.getPath(request, true)
        when (request.source) {
            LOCAL -> {
                try {
                    library.root.edges[LOCAL]?.let {
                        request.query?.let { query ->
                            searchMusic(it, query).forEach { dir.add(it) }
                            callback?.onSuccess()
                        } ?: run {
                            addToCategory(it, localSource.tracks())
                            callback?.onSuccess()
                        }
                    } ?: callback?.onError("Can not browse local media")
                } catch (e: Exception) {
                    callback?.onError("error when loading local media", e)
                }
            }

            CUE_POINTS -> {
                MusicService.database.cuePointItems.forEach { item ->
                    run {
                        dir.add(item)
                        updateExtendedMetadata(item.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
                    }
                }
                dir.sort()
                callback?.onSuccess()
            }

            else -> {
                if (!library.root.edges.containsKey(request.source)) {
                    callback?.onError("unknown source ${request.source}")
                    return
                }
                val plugin = PluginManager.plugins[request.source] ?: let {
                    callback?.onError("no plugin for source ${request.source}")
                    return
                }
                val refresh = (request.options.getBoolean(OPTION_REFRESH))
                val res = object :
                    com.tiefensuche.soundcrowd.plugins.Callback<List<MediaMetadataCompat>> {
                    override fun onResult(result: List<MediaMetadataCompat>) {
                        try {
                            if (refresh) {
                                dir.clear()
                            }
                            for (item in result.map { item ->
                                MediaMetadataCompat.Builder(item).putString(
                                    MediaMetadataCompatExt.METADATA_KEY_SOURCE,
                                    plugin.name()
                                ).build()
                            }) {
                                dir.add(item)
                            }
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
                } catch (e: java.lang.Error) {
                    callback?.onError("Error when requesting plugin: ${e.message}")
                }
            }
        }
    }

    internal fun resolve(uri: Uri): String {
        val track = localSource.resolve(uri)
        val request = Request(TEMP + LEAF_SEPARATOR + track.description.mediaId)
        val dir = library.getPath(request, true)
        dir.add(track)
        return request.mediaId
    }

    fun favorite(
        metadata: MediaMetadataCompat,
        callback: com.tiefensuche.soundcrowd.plugins.Callback<Boolean>
    ) {
        try {
            PluginManager.plugins[metadata.getString(MediaMetadataCompatExt.METADATA_KEY_SOURCE)]?.favorite(
                metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID),
                callback
            )
                ?: callback.onResult(false)
        } catch (e: Exception) {
            callback.onResult(false)
        }
    }

    private fun getCuePoints(): BrowsableItem {
        val request = Request(CUE_POINTS)
        val dir = try {
            library.getPath(request, false)
        } catch (ex: Exception) {
            retrieveMedia(request, null)
            library.getPath(request, false)
        }
        return dir
    }

    fun addCuePoint(musicId: String, position: Int, description: String = "") {
        val metadata = getMusic(musicId) ?: return
        val dir = getCuePoints()
        dir.add(metadata)
        MusicService.database.addCuePoint(metadata, position, description)
        val cues = JSONArray(metadata.getString(Cues.CUES))
            .put(
                JSONObject()
                    .put(Cues.POSITION, position)
                    .put(Cues.DESCRIPTION, description)
            )
        updateMetadata(
            MediaMetadataCompat.Builder(metadata)
                .putString(Cues.CUES, cues.toString())
                .build()
        )
    }

    fun setCuePoint(musicId: String, position: Int, description: String = "") {
        val metadata = getMusic(musicId) ?: return
        MusicService.database.setDescription(musicId, position, description)
        val cues = JSONArray(metadata.getString(Cues.CUES))
        for (i in 0 until cues.length()) {
            val cue = cues.getJSONObject(i)
            if (cue.getInt(Cues.POSITION) == position) {
                cue.put(Cues.DESCRIPTION, description)
                cues.put(i, cue)
                break
            }
        }
        updateMetadata(
            MediaMetadataCompat.Builder(metadata)
                .putString(Cues.CUES, cues.toString())
                .build()
        )
    }

    fun deleteCuePoint(musicId: String, position: Int) {
        val metadata = getMusic(musicId) ?: return
        MusicService.database.deleteCuePoint(musicId, position)
        val cues = JSONArray(metadata.getString(Cues.CUES))
        for (i in 0 until cues.length()) {
            val cue = cues.getJSONObject(i)
            if (cue.getInt(Cues.POSITION) == position) {
                cues.remove(i)
                break
            }
        }
        updateMetadata(
            MediaMetadataCompat.Builder(metadata)
                .putString(Cues.CUES, cues.toString())
                .build()
        )
    }

    fun updateMetadata(metadata: MediaMetadataCompat) {
        metadata.description?.mediaId?.let {
            library.keys[it]?.metadata = metadata
        }
    }

    fun updateLastPosition(metadata: MediaMetadataCompat, position: Long) {
        MusicService.database.updatePosition(metadata, position)
        val musicId = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
        library.keys[musicId]?.metadata = MediaMetadataCompat.Builder(getMusic(musicId))
            .putLong(Cues.LAST_POSITION, position).build()
    }

    interface Callback {
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

    object Cues {
        const val CUES = "CUES"
        const val POSITION = "POSITION"
        const val DESCRIPTION = "DESCRIPTION"
        const val LAST_POSITION = "LAST_POSITION"
    }

    companion object {
        const val ACTION_GET_MEDIA = "GET_MEDIA"
        const val ACTION_GET_PLUGINS = "GET_PLUGINS"
        const val MEDIA_ID = "MEDIA_ID"
        const val OFFSET = "OFFSET"
        const val QUERY = "QUERY"
        const val OPTION_REFRESH = "REFRESH"
        const val RESULT = "RESULT"
        private val TAG = MusicProvider::class.simpleName
    }
}