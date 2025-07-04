package com.tiefensuche.soundcrowd.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionError.ERROR_INVALID_STATE
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.playback.EqualizerControl
import com.tiefensuche.soundcrowd.playback.RecordAudioBufferSink
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.sources.LocalSource
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Cues.POSITION
import com.tiefensuche.soundcrowd.ui.MusicPlayerActivity
import io.github.tiefensuche.SongRec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class PlaybackService : MediaLibraryService() {

    companion object {
        private const val NOTIFICATION_ID = 412
        private const val CHANNEL_ID = "soundcrowd_session_notification_channel"
        private const val REQUEST_CODE = 100

        internal const val COMMAND_GET_PLUGINS = "COMMAND_GET_PLUGINS"
        internal const val COMMAND_RESOLVE = "COMMAND_RESOLVE"
        internal const val COMMAND_CUE_CREATE = "COMMAND_CUE_CREATE"
        internal const val COMMAND_CUE_EDIT = "COMMAND_CUE_EDIT"
        internal const val COMMAND_CUE_DELETE = "COMMAND_CUE_DELETE"
        internal const val COMMAND_LIKE = "COMMAND_LIKE"
        internal const val COMMAND_PLAYLIST_CREATE = "COMMAND_PLAYLIST_CREATE"
        internal const val COMMAND_PLAYLIST_ADD = "COMMAND_PLAYLIST_ADD"
        internal const val COMMAND_PLAYLIST_MOVE = "COMMAND_PLAYLIST_MOVE"
        internal const val COMMAND_PLAYLIST_REMOVE = "COMMAND_PLAYLIST_REMOVE"
        internal const val COMMAND_PLAYLIST_DELETE = "COMMAND_PLAYLIST_DELETE"
        internal const val COMMAND_START_TAGGING = "COMMAND_START_TAGGING"

        internal const val ARG_NAME = "NAME"
        internal const val ARG_PLAYLIST_ID = "PLAYLIST_ID"
        internal const val ARG_POSITION = "POSITION"

        internal const val RESULT = "RESULT"
        internal const val ARG_ERROR = "ARG_ERROR"
        internal const val ARG_URI = "ARG_URI"

        internal lateinit var musicProvider : MusicProvider

        // The function allowing looking up non-playable music metadata through
        // the usage of the MusicProvider
        fun getMusic(musicId : String) : MediaItem? {
            return musicProvider.getMusic(musicId)
        }
    }

    var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var cache: SimpleCache
    private val songRec = SongRec()
    lateinit var sink: RecordAudioBufferSink
    private var callback: MediaLibrarySession.Callback =
    object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val result = super.onConnect(session, controller)
            return MediaSession.ConnectionResult.accept(
                result.availableSessionCommands
                    .buildUpon()
                    .add(SessionCommand(COMMAND_GET_PLUGINS, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_RESOLVE, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_CUE_CREATE, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_CUE_EDIT, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_CUE_DELETE, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_LIKE, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_PLAYLIST_CREATE, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_PLAYLIST_ADD, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_PLAYLIST_MOVE, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_PLAYLIST_REMOVE, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_PLAYLIST_DELETE, Bundle.EMPTY))
                    .add(SessionCommand(COMMAND_START_TAGGING, Bundle.EMPTY))
                    .build(), result.availablePlayerCommands
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val test = musicProvider.getMusic(mediaId.substringAfterLast('/'))
            return if (test != null)
                Futures.immediateFuture(LibraryResult.ofItem(test, null))
            else
                Futures.immediateCancelledFuture()
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val settableFuture = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    val request = musicProvider.Request(parentId, page, params?.extras?.getBoolean(MusicProvider.OPTION_REFRESH) ?: false)
                    try {
                        settableFuture.set(LibraryResult.ofItemList(musicProvider.getChildren(request), params))
                    } catch (ex: Exception) {
                        settableFuture.set(LibraryResult.ofError(ERROR_INVALID_STATE,
                            LibraryParams.Builder()
                                .setExtras(Bundle().apply { putString(ARG_ERROR, ex.message) })
                                .build()
                            )
                        )
                    }
                }
            }
            return settableFuture
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            return Futures.immediateFuture(mediaItems)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            browser: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            return Futures.immediateFuture(
                MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_GET_PLUGINS -> {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, Bundle().apply { putParcelableArrayList(RESULT, musicProvider.getPlugins()) }))
                }
                COMMAND_RESOLVE -> {
                    val uri = args.getParcelable<Uri>(ARG_URI)
                    if (!PluginManager.handleCallback(uri!!)) {
                        val mediaItem = musicProvider.resolve(uri)
                        session.player.setMediaItem(mediaItem)
                        session.player.prepare()
                        session.player.play()
                    }
                }
                COMMAND_CUE_CREATE -> {
                    musicProvider.addCuePoint(session.player.currentMediaItem!!.mediaId, args.getInt(POSITION), args.getString(MusicProvider.Cues.DESCRIPTION))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_CUE_EDIT -> {
                    musicProvider.setCuePoint(args.getString(MEDIA_ID)!!, args.getInt(POSITION), args.getString(MusicProvider.Cues.DESCRIPTION))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_CUE_DELETE -> {
                    musicProvider.deleteCuePoint(args.getString(MEDIA_ID)!!, args.getInt(POSITION))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_LIKE -> {
                    val settableFuture = SettableFuture.create<SessionResult>()
                    val mediaId = args.getString(MEDIA_ID)!!
                    val mediaItem =
                        if (session.player.currentMediaItem?.mediaId == mediaId)
                            session.player.currentMediaItem!!
                        else
                            getMusic(mediaId)!!
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = withContext(Dispatchers.IO) {
                            musicProvider.favorite(mediaItem)
                        }
                        if (result && session.player.currentMediaItem == mediaItem)
                            session.player.replaceMediaItem(session.player.currentMediaItemIndex, getMusic(mediaId)!!)
                        settableFuture.set(SessionResult(if (result) SessionResult.RESULT_SUCCESS else SessionError.ERROR_UNKNOWN))
                    }
                    return settableFuture
                }
                COMMAND_START_TAGGING -> {
                    val settableFuture = SettableFuture.create<SessionResult>()
                    tag(filesDir.path + "/rec.wav", settableFuture)
                    return settableFuture
                }
                COMMAND_PLAYLIST_CREATE -> {
                    musicProvider.createPlaylist(args.getString(ARG_NAME)!!, args.getString(MediaConstants.EXTRA_KEY_MEDIA_ID)!!)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_PLAYLIST_ADD -> {
                    musicProvider.addPlaylist(args.getString(ARG_PLAYLIST_ID)!!, args.getString(MediaConstants.EXTRA_KEY_MEDIA_ID)!!)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_PLAYLIST_MOVE -> {
                    musicProvider.movePlaylist(args.getString(ARG_PLAYLIST_ID)!!, args.getString(MediaConstants.EXTRA_KEY_MEDIA_ID)!!, args.getInt(ARG_POSITION))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_PLAYLIST_REMOVE -> {
                    musicProvider.removePlaylist(args.getString(ARG_PLAYLIST_ID)!!, args.getString(MediaConstants.EXTRA_KEY_MEDIA_ID)!!)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                COMMAND_PLAYLIST_DELETE -> {
                    musicProvider.deletePlaylist(args.getString(MediaConstants.EXTRA_KEY_MEDIA_ID)!!)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return Futures.immediateFailedFuture(Exception("Can not handle: ${customCommand.customAction}"))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onCreate() {
        super.onCreate()
        cache = SimpleCache(
            File(this.cacheDir, "exoplayer"),
            LeastRecentlyUsedCacheEvictor(
                1024L * 1024 * getDefaultSharedPreferences(this).getInt(
                    getString(R.string.cache_size_key),
                    512
                )
            ),
            StandaloneDatabaseProvider(this)
        )
        val cacheSink = CacheDataSink.Factory()
            .setCache(cache)
        val upstreamFactory =
            ResolvingDataSource.Factory(DefaultHttpDataSource.Factory()) { dataSpec: DataSpec ->
                // Provide just-in-time URI resolution logic.
                dataSpec.withUri(musicProvider.resolveMusic(dataSpec.key!!)!!)
            }
        val downStreamFactory = FileDataSource.Factory()
        val cacheDataSourceFactory  =
            CacheDataSource.Factory()
                .setCache(cache)
                .setCacheWriteDataSinkFactory(cacheSink)
                .setCacheReadDataSourceFactory(downStreamFactory)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val dataSourceFactory = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
        val defaultMediaSourceFactory = DefaultMediaSourceFactory(this)

        class CustomMediaSourceFactory : MediaSource.Factory {
            override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
                TODO("Not yet implemented")
            }

            override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
                TODO("Not yet implemented")
            }

            override fun getSupportedTypes(): IntArray {
                TODO("Not yet implemented")
            }

            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val plugin = mediaItem.mediaMetadata.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_PLUGIN)
                val isDataSource = mediaItem.mediaMetadata.extras?.getBoolean(MediaMetadataCompatExt.METADATA_KEY_DATASOURCE) ?: false
                return when (plugin) {
                    LocalSource.NAME -> {
                        defaultMediaSourceFactory.createMediaSource(mediaItem.buildUpon().setUri(mediaItem.requestMetadata.mediaUri).build())
                    }
                    else -> {
                        if (!isDataSource)
                            dataSourceFactory.createMediaSource(mediaItem.buildUpon().setUri(mediaItem.mediaId).build())
                        else
                            ProgressiveMediaSource.Factory { musicProvider.getDataSource(mediaItem.mediaId)!! }
                                .createMediaSource(mediaItem.buildUpon().setUri(mediaItem.requestMetadata.mediaUri).build())
                    }
                }
            }
        }

        sink = RecordAudioBufferSink(this.filesDir.path + "/rec.wav")
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setMediaSourceFactory(CustomMediaSourceFactory())
            .setHandleAudioBecomingNoisy(true)
            .setRenderersFactory(object : DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): AudioSink {
                    return DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(arrayOf(TeeAudioProcessor(sink)))
                        .build()
                }
            })
            .build()
        player.addAnalyticsListener(EventLogger())
        setListener(MediaSessionServiceListener())
        EqualizerControl.setupEqualizerFX(player.audioSessionId, this)

        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                mediaLibrarySession?.setCustomLayout(
                    if (mediaMetadata.supportedCommands.contains(COMMAND_LIKE))
                        (mediaMetadata.userRating as? HeartRating)?.isHeart?.let {
                            ImmutableList.of(
                                CommandButton.Builder(CommandButton.ICON_HEART_FILLED)
                                    .setDisplayName(getString(R.string.favorite))
                                    .setIconResId(if (it) androidx.media3.session.R.drawable.media3_icon_heart_filled else androidx.media3.session.R.drawable.media3_icon_heart_unfilled)
                                    .setSessionCommand(SessionCommand(COMMAND_LIKE, Bundle().apply { putString(MEDIA_ID, player.currentMediaItem!!.mediaId) }))
                                    .build()
                            )
                        } ?: emptyList()
                    else
                        emptyList()
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null)
                    return
                musicProvider.updateExtendedMetadata(mediaItem)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                musicProvider.updatePlaylist("0", MutableList(player.mediaItemCount, player::getMediaItemAt))
            }
        })

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(createContentIntent())
            .build()

        musicProvider = MusicProvider(this)
        player.setMediaItems(musicProvider.getPlaylist("0"))
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            cache.release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    internal fun tag(filename: String, result: SettableFuture<SessionResult>) {
        val mediaId = mediaLibrarySession!!.player.currentMediaItem!!.mediaId
        val position = mediaLibrarySession!!.player.currentPosition
        AsyncTask.execute {
            val data = Bundle()
            sink.setRecord(true)
            Thread.sleep(10000)
            sink.setRecord(false)
            val signature = songRec.makeSignatureFromFile(filename)
            val response = songRec.recognizeSongFromSignature(signature)
            val json = JSONObject(response)
            if (json.has("track")) {
                val track = json.getJSONObject("track")
                val result = "${track.getString("subtitle")} - ${track.getString("title")}"
                musicProvider.addCuePoint(mediaId, position.toInt(), result)
                data.putInt(POSITION, position.toInt())
                data.putString(RESULT, result)
            }
            result.set(SessionResult(SessionResult.RESULT_SUCCESS, data))
        }
    }

    private fun createContentIntent(): PendingIntent {
        val openUI = Intent(this, MusicPlayerActivity::class.java)
        openUI.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        openUI.putExtra(MusicPlayerActivity.EXTRA_START_FULLSCREEN, true)
        return PendingIntent.getActivity(this, REQUEST_CODE, openUI,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private inner class MediaSessionServiceListener : Listener {

        /**
         * This method is only required to be implemented on Android 12 or above when an attempt is made
         * by a media controller to resume playback when the {@link MediaSessionService} is in the
         * background.
         */
        override fun onForegroundServiceStartNotAllowedException() {
            if (
                Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // Notification permission is required but not granted
                return
            }
            val notificationManagerCompat = NotificationManagerCompat.from(this@PlaybackService)
            ensureNotificationChannel(notificationManagerCompat)
            val builder =
                NotificationCompat.Builder(this@PlaybackService, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.app_name))
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(getString(R.string.app_name))
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(createContentIntent())
            notificationManagerCompat.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun ensureNotificationChannel(notificationManagerCompat: NotificationManagerCompat) {
        if (
            Build.VERSION.SDK_INT < 26 ||
            notificationManagerCompat.getNotificationChannel(CHANNEL_ID) != null
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        notificationManagerCompat.createNotificationChannel(channel)
    }
}