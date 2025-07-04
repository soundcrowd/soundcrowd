/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.sources

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import com.tiefensuche.soundcrowd.playback.MediaDataSourceWrapper
import com.tiefensuche.soundcrowd.plugins.MediaItemUtils
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.service.Database
import com.tiefensuche.soundcrowd.service.PluginManager
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.CUE_POINTS
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.HISTORY
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LOCAL
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.PLAYLISTS
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.TEMP
import com.tiefensuche.soundcrowd.utils.MediaIDHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.CATEGORY_SEPARATOR
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.LEAF_SEPARATOR
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.extractMusicIDFromMediaID
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.getHierarchy
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple data provider for music tracks. The actual metadata source is delegated to a
 * MusicProviderSource defined by a constructor argument of this class.
 */
internal class MusicProvider(context: Context) {

    private val database = Database(context)

    class Library {
        val root = BrowsableItem(this, "ROOT")
        val keys = HashMap<String, MediaItem>()

        @Throws(Exception::class)
        fun getPath(request: Request, create: Boolean): BrowsableItem {
            var dir = root
            for (dirName in request.hierarchy) {
                dir.edges[dirName]?.let { dir = it } ?: let {
                    if (!create)
                        throw Exception("Directory $dirName does not exist!")
                    val item = BrowsableItem(this, dirName)
                    dir.edges[dirName] = item
                    dir = item
                }
            }

            return dir
        }
    }

    class BrowsableItem(private val library: Library, val id: String) {
        val items = ArrayList<String>()
        val edges = ConcurrentHashMap<String, BrowsableItem>()
        private val musicIds = HashSet<String>()

        val item : MediaItem?
            get() = library.keys[id]

        fun items(): List<MediaItem> {
            return items.mapNotNull { item -> library.keys[item] }
        }

        fun add(newItem: MediaItem) {
            val musicId = newItem.mediaId
            if (musicIds.contains(musicId))
                return

            if (!library.keys.containsKey(musicId)) {
                library.keys[musicId] = newItem
            }
            items.add(musicId)
            musicIds.add(musicId)
        }

        fun sort() {
            items.sortBy { item ->
                library.keys[item]?.mediaMetadata?.title.toString().uppercase()
            }
        }

        fun clear() {
            items.clear()
            musicIds.clear()
        }
    }

    private val library = Library()
    private val localSource: LocalSource = LocalSource(context)
    private val pluginManager: PluginManager = PluginManager(context)

    inner class Request(
        val mediaId: String,
        val offset: Int = 0,
        val refresh: Boolean = false
    ) {

        lateinit var hierarchy: Array<String>
        lateinit var source: String
        var category: String? = null
        var path: String? = null
        var cmd: String? = null
        var type: String? = null
        var query: String? = null

        init {
            fromMediaId(mediaId)
        }

        private fun parseCommand(cmd: String, type: String, query: String): Boolean {
            if (!listOf(QUERY, SUGGESTIONS).contains(cmd))
                return false

            this.cmd = cmd
            this.type = type
            this.query = query
            return true
        }

        private fun fromMediaId(mediaId: String) {
            hierarchy = getHierarchy(mediaId)
            for (i in hierarchy.indices) {
                if (i < hierarchy.size - 2 && parseCommand(hierarchy[i], hierarchy[i + 1], hierarchy[i + 2])) {
                    if (i + 3 == hierarchy.size - 1 && path == null) {
                        path = hierarchy[i + 3]
                    }
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

    private fun getMusicByCategory(mediaId: String): List<MediaItem> {
        val dir = library.getPath(Request(mediaId), false)
        val res = ArrayList<MediaItem>()
        for (item in dir.items())
            if (item.mediaMetadata.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE) == MediaMetadataCompatExt.MediaType.MEDIA.name)
                res.add(item)

        return res
    }

    /**
     * Return the MediaMetadataCompat for the given musicID.
     *
     * @param musicId The unique, non-hierarchical music ID.
     */
    internal fun getMusic(musicId: String): MediaItem? {
        if (library.keys.containsKey(musicId))
            return library.keys[musicId]
        return database.getMediaItem(musicId)
    }

    internal fun updateExtendedMetadata(item: MediaItem) {
        val jsonList = JSONArray()
        val cuePoints = database.getCuePoints(item.mediaId)
        for (cuePoint in cuePoints) {
            val json = JSONObject()
                .put(Cues.POSITION, cuePoint.position)
                .put(Cues.DESCRIPTION, cuePoint.description)
            jsonList.put(json)
        }

        item.mediaMetadata.extras!!
            .putLong(
                Cues.LAST_POSITION,
                database.getLastPosition(item.mediaId)
            )
        item.mediaMetadata.extras!!.putString(Cues.CUES, jsonList.toString())
    }

    internal fun resolveMusic(mediaId: String) : Uri? {
        getMusic(extractMusicIDFromMediaID(mediaId))?.let { metadata ->
            val plugin = metadata.mediaMetadata.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_PLUGIN)
            PluginManager.plugins[plugin]?.let {
                return it.getMediaUri(metadata)
            } ?: Log.d(TAG, "no plugin found to resolve $mediaId")
        }
        return null
    }

    internal fun getDataSource(mediaId: String): DataSource? {
        getMusic(extractMusicIDFromMediaID(mediaId))?.let { metadata ->
            val plugin = metadata.mediaMetadata.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_PLUGIN)
            PluginManager.plugins[plugin]?.let {
                return MediaDataSourceWrapper(it.getDataSource(metadata)!!)
            } ?: Log.d(TAG, "no plugin found to resolve $mediaId")
        }
        return null
    }

    private fun addToCategory(dir: BrowsableItem, items: ArrayList<MediaItem>) {
        items.sortBy { metadata ->
            metadata.mediaMetadata.title.toString().uppercase()
        }
        for (item in items) {
            dir.add(item)
            for (key in listOf(
                Pair(METADATA_KEY_ARTIST, item.mediaMetadata.artist),
                Pair(METADATA_KEY_ALBUM, item.mediaMetadata.albumTitle)
            )) {
                var category = dir.edges[key.first]
                if (category == null) {
                    category = BrowsableItem(library, key.first)
                    dir.edges[key.first] = category
                }
                val value = MediaIDHelper.toBrowsableName(key.second.toString())
                var node = category.edges[value]
                if (node == null) {
                    node = BrowsableItem(library, value)
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
            METADATA_KEY_ARTIST,
            METADATA_KEY_ALBUM
        )) {
            dir.edges[category]?.let {
                for ((key, value) in it.edges) {
                    val mediaItem = value.items()[0]
                    val bundle = Bundle()
                    bundle.putString(
                        MediaMetadataCompatExt.METADATA_KEY_TYPE,
                        MediaMetadataCompatExt.MediaType.COLLECTION.name
                    )
                    val subItem = MediaItemUtils.createBrowsableItem(
                        key,
                        if (category == METADATA_KEY_ARTIST) mediaItem.mediaMetadata.artist.toString() else mediaItem.mediaMetadata.albumTitle.toString(),
                        MediaMetadataCompatExt.MediaType.COLLECTION,
                        if (category != METADATA_KEY_ARTIST) mediaItem.mediaMetadata.artist.toString() else null,
                        mediaItem.mediaMetadata.albumTitle.toString(),
                        mediaItem.mediaMetadata.artworkUri,
                        getDurationSum(getMusicByCategory(LOCAL + CATEGORY_SEPARATOR + category + CATEGORY_SEPARATOR + key)),
                    )
                    it.add(subItem)
                }
                it.sort()
            }
        }
    }

    private fun hasItems(request: Request): Boolean {
        if (request.refresh)
            return false
        if (request.source == PLAYLISTS)
            return false

        return try {
            library.getPath(request, false).items.size > request.offset
        } catch (e: Exception) {
            false
        }
    }

    internal fun getChildren(request: Request): List<MediaItem> {
        if (request.mediaId == HISTORY)
            return database.getHistory()

        if (request.cmd == SUGGESTIONS)
            return getSuggestions(request)

        if (request.cmd == QUERY)
            database.addSearchQuery(request.query!!)

        if (!hasItems(request)) {
            retrieveMedia(request)
        }
        val items = library.getPath(request, false).items()
        return items.subList(request.offset, items.size)
    }

   private fun getDurationSum(tracks: List<MediaItem>): Long {
        var sum: Long = 0
        for (metadata in tracks) {
            sum += metadata.mediaMetadata.durationMs ?: 0
        }
        return sum
    }

    private fun searchMusic(dir: BrowsableItem, query: String): List<MediaItem> {
        val result = ArrayList<MediaItem>()
        for (item in dir.items()) {
            for (value in listOf(item.mediaMetadata.title, item.mediaMetadata.artist, item.mediaMetadata.albumTitle)) {
                if (value.toString().lowercase(Locale.US).contains(query.lowercase(Locale.US))) {
                    result.add(item)
                    break
                }
            }
        }
        return result
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
                categories.add(Bundle().apply {
                    putString(
                        PluginMetadata.NAME,
                        plugin.name() + CATEGORY_SEPARATOR + category
                    )
                    putString(PluginMetadata.CATEGORY, category)
                    putString(
                        PluginMetadata.MEDIA_TYPE,
                        MediaMetadataCompatExt.MediaType.STREAM.name
                    )
                })
            }

            val searchCategories = ArrayList<Bundle>()
            for (category in plugin.searchCategories()) {
                searchCategories.add(Bundle().apply {
                    putString(PluginMetadata.NAME, category)
                    putString(PluginMetadata.CATEGORY, category)
                })
            }

            bundle.putParcelableArrayList(PluginMetadata.CATEGORY, categories)
            bundle.putParcelableArrayList(PluginMetadata.SEARCH_TYPES, searchCategories)
            plugins.add(bundle)
        }

        return plugins
    }

    private fun retrieveMedia(request: Request) {
        val dir = library.getPath(request, true)
        when (request.source) {
            LOCAL -> {
                try {
                    library.root.edges[LOCAL]?.let {
                        request.query?.let { query ->
                            searchMusic(it, query).forEach { dir.add(it) }
                        } ?: run {
                            addToCategory(it, localSource.tracks())
                        }
                    } ?: throw Exception("Can not browse local media")
                } catch (e: Exception) {
                    throw Exception("error when loading local media", e)
                }
            }
            PLAYLISTS -> {
                if (request.query != null) {
                    (if (request.path != null)
                        getPlaylist(request.path!!)
                    else
                        searchMusic(
                            library.root.edges[PLAYLISTS]!!,
                            request.query!!
                        )).forEach { dir.add(it) }
                    return
                }
                dir.clear()
                (request.category?.let {
                    getPlaylist(it)
                } ?: database.getPlaylists()).forEach {
                    dir.add(it)
                }
            }
            CUE_POINTS -> {
                if (request.query != null) {
                    searchMusic(library.root.edges[CUE_POINTS]!!, request.query!!).forEach { dir.add(it) }
                    return
                }
                database.getCuePointItems().forEach { item ->
                    run {
                        dir.add(item)
                        getMusic(item.mediaId)?.let { updateExtendedMetadata(it) }
                    }
                }
                dir.sort()
            }
            else -> {
                if (!library.root.edges.containsKey(request.source)) {
                    throw Exception("unknown source ${request.source}")
                }
                val plugin = PluginManager.plugins[request.source] ?: let {
                    throw Exception("no plugin for source ${request.source}")
                }
                val result = request.query?.let {
                    plugin.getMediaItems(QUERY, request.path ?: "", it, request.type!!, request.refresh)
                } ?: request.path?.let {
                    plugin.getMediaItems(request.category!!, it, request.refresh)
                } ?: plugin.getMediaItems(request.category!!, request.refresh)
                if (request.refresh) {
                    dir.clear()
                }
                result.forEach {
                    it.mediaMetadata.extras?.putString(
                        MediaMetadataCompatExt.METADATA_KEY_PLUGIN,
                        plugin.name()
                    )
                    dir.add(it)
                }
            }
        }
    }

    internal fun resolve(uri: Uri): MediaItem {
        val track = localSource.resolve(uri)
        val request = Request(TEMP + LEAF_SEPARATOR + track.mediaId)
        val dir = library.getPath(request, true)
        dir.add(track)
        return track
    }

    fun favorite(
        mediaItem: MediaItem
    ): Boolean {
        val result = PluginManager.plugins[mediaItem.mediaMetadata.extras!!.getString(MediaMetadataCompatExt.METADATA_KEY_PLUGIN)]!!.favorite(mediaItem.mediaId)
        if (result)
            updateMetadata(
                mediaItem.buildUpon().setMediaMetadata(
                    mediaItem.mediaMetadata.buildUpon()
                        .setUserRating(HeartRating(!(mediaItem.mediaMetadata.userRating as HeartRating).isHeart))
                        .build()
                ).build()
            )
        return result
    }

    fun createPlaylist(name: String, musicId: String) {
        val metadata = getMusic(musicId) ?: return
        database.createPlaylist(name, metadata)
    }

    fun updatePlaylist(playlistId: String, tracks: List<MediaItem>) {
        database.updatePlaylist(playlistId, tracks)
    }

    fun addPlaylist(playlistId: String, musicId: String) {
        val metadata = getMusic(musicId) ?: return
        database.addPlaylist(playlistId, metadata)
    }

    fun movePlaylist(playlistId: String, musicId: String, position: Int) {
        database.movePlaylist(playlistId, musicId, position)
    }

    fun removePlaylist(playlistId: String, musicId: String) {
        database.removePlaylist(playlistId, musicId)
    }

    fun deletePlaylist(playlistId: String) {
        database.deletePlaylist(playlistId)
    }

    fun getPlaylist(playlistId: String): List<MediaItem> {
        return database.getPlaylist(playlistId).mapNotNull { getMusic(it) ?: database.getMediaItem(it) }
    }

    private fun getSuggestions(request: Request): List<MediaItem> {
        val res = ArrayList<MediaItem>()
        if (request.query!!.isNotEmpty()) {
            when (request.source) {
                LOCAL -> {
                    val unique = HashSet<CharSequence?>()
                    request.query!!.lowercase().let {
                        for (item in library.getPath(Request(LOCAL), false).items()) {
                            if (item.mediaMetadata.title?.toString()?.lowercase()?.contains(it) == true)
                                unique.add(item.mediaMetadata.title)
                        }
                        for (item in library.getPath(Request("$LOCAL/METADATA_KEY_ARTIST"), false).items()) {
                            if (item.mediaMetadata.artist?.toString()?.lowercase()?.contains(it) == true)
                                unique.add(item.mediaMetadata.artist)
                        }
                        for (item in library.getPath(Request("$LOCAL/METADATA_KEY_ALBUM"), false).items()) {
                            if (item.mediaMetadata.albumTitle?.toString()?.lowercase()?.contains(it) == true)
                                unique.add(item.mediaMetadata.albumTitle)
                        }
                    }
                    unique.forEach {
                        res.add(MediaItemUtils.createTextItem(it.toString(), false))
                    }
                }
                else -> {
                    res.addAll(PluginManager.plugins[request.source]?.getSuggestions(request.type!!, request.query!!)
                        ?.map { MediaItemUtils.createTextItem(it, false) } ?: emptyList())
                }
            }
        }

        res.addAll(database.getRecentSearchQueries(request.query))
        return res
    }

    private fun getCuePoints(): BrowsableItem {
        val request = Request(CUE_POINTS)
        val dir = try {
            library.getPath(request, false)
        } catch (ex: Exception) {
            retrieveMedia(request)
            library.getPath(request, false)
        }
        return dir
    }

    fun addCuePoint(musicId: String, position: Int, description: String? = null) {
        val metadata = getMusic(musicId) ?: return
        val dir = getCuePoints()
        dir.add(metadata)
        database.addCuePoint(metadata, position, description)
        JSONArray()
        val cues = metadata.mediaMetadata.extras?.let {
            if (it.containsKey(Cues.CUES))
                JSONArray(it.getString(Cues.CUES)!!)
            else
                JSONArray()
        } ?: JSONArray()
        cues.put(
            JSONObject()
                .put(Cues.POSITION, position)
                .put(Cues.DESCRIPTION, description)
        )
        metadata.mediaMetadata.extras?.putString(Cues.CUES, cues.toString())
        updateMetadata(metadata)
    }

    fun setCuePoint(musicId: String, position: Int, description: String? = null) {
        val metadata = getMusic(musicId) ?: return
        database.setDescription(musicId, position, description)
        val cues = JSONArray(metadata.mediaMetadata.extras?.getString(Cues.CUES))
        for (i in 0 until cues.length()) {
            val cue = cues.getJSONObject(i)
            if (cue.getInt(Cues.POSITION) == position) {
                cue.put(Cues.DESCRIPTION, description)
                cues.put(i, cue)
                break
            }
        }
        metadata.mediaMetadata.extras?.putString(Cues.CUES, cues.toString())
        updateMetadata(metadata)
    }

    fun deleteCuePoint(musicId: String, position: Int) {
        val metadata = getMusic(musicId) ?: return
        database.deleteCuePoint(musicId, position)
        val cues = JSONArray(metadata.mediaMetadata.extras?.getString(Cues.CUES))
        for (i in 0 until cues.length()) {
            val cue = cues.getJSONObject(i)
            if (cue.getInt(Cues.POSITION) == position) {
                cues.remove(i)
                break
            }
        }
        metadata.mediaMetadata.extras?.putString(Cues.CUES, cues.toString())
        updateMetadata(metadata)
    }

    private fun updateMetadata(metadata: MediaItem) {
        metadata.mediaId.let {
            library.keys[it] = metadata
        }
    }

    fun updateLastPosition(mediaItem: MediaItem?, position: Long) {
        if (mediaItem == null)
            return
        database.updatePosition(mediaItem, position)
        val musicId = mediaItem.mediaId
        library.keys[musicId]?.mediaMetadata?.extras?.putLong(Cues.LAST_POSITION, position)
    }

    internal fun increasePlayCount(item: MediaItem) {
        database.increasePlayCount(item)
    }

    object Media {
        const val LOCAL = "LOCAL"
        const val PLAYLISTS = "PLAYLISTS"
        const val CUE_POINTS = "CUE_POINTS"
        const val HISTORY = "HISTORY"
        const val TEMP = "TEMP"
    }

    object PluginMetadata {
        const val NAME = "NAME"
        const val CATEGORY = "CATEGORY"
        const val SEARCH_TYPES = "SEARCH_TYPES"
        const val MEDIA_TYPE = "MEDIA_TYPE"
        const val ICON = "ICON"
    }

    object Cues {
        const val CUES = "CUES"
        const val POSITION = "POSITION"
        const val DESCRIPTION = "DESCRIPTION"
        const val LAST_POSITION = "LAST_POSITION"
    }

    companion object {
        const val MEDIA_ID = "MEDIA_ID"
        const val QUERY = "QUERY"
        const val SUGGESTIONS = "SUGGESTIONS"
        const val OPTION_REFRESH = "REFRESH"
        const val METADATA_KEY_ARTIST = "METADATA_KEY_ARTIST"
        const val METADATA_KEY_ALBUM = "METADATA_KEY_ALBUM"
        private val TAG = MusicProvider::class.simpleName
    }
}