/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.service


import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.*
import android.preference.PreferenceManager
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.database.DatabaseHelper
import com.tiefensuche.soundcrowd.playback.LocalPlayback
import com.tiefensuche.soundcrowd.playback.PlaybackManager
import com.tiefensuche.soundcrowd.playback.QueueManager
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.utils.LogHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_ROOT
import java.lang.ref.WeakReference
import java.util.*


/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 *
 * To implement a MediaBrowserService, you need to:
 *
 *
 *
 *  *  Extend [android.service.media.MediaBrowserService], implementing the media browsing
 * related methods [android.service.media.MediaBrowserService.onGetRoot] and
 * [android.service.media.MediaBrowserService.onLoadChildren];
 *  *  In onCreate, start a new [android.media.session.MediaSession] and notify its parent
 * with the session's token [android.service.media.MediaBrowserService.setSessionToken];
 *
 *  *  Set a callback on the
 * [android.media.session.MediaSession.setCallback].
 * The callback will receive all the user's actions, like play, pause, etc;
 *
 *  *  Handle all the actual music playing using any method your app prefers (for example,
 * [android.media.MediaPlayer])
 *
 *  *  Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * [android.media.session.MediaSession.setPlaybackState]
 * [android.media.session.MediaSession.setMetadata] and
 * [android.media.session.MediaSession.setQueue])
 *
 *  *  Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 *
 *
 *
 * To make your app compatible with Android Auto, you also need to:
 *
 *
 *
 *  *  Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 *
 *
 * @see [README.md](README.md) for more details.
 */
class MusicService : MediaBrowserServiceCompat(), PlaybackManager.PlaybackServiceCallback {
    private val mDelayedStopHandler = DelayedStopHandler(this)
    private lateinit var mPlaybackManager: PlaybackManager
    private lateinit var mSession: MediaSessionCompat
    private lateinit var mMediaNotificationManager: MediaNotificationManager
    private lateinit var mQueueManager: QueueManager
    private lateinit var databaseHelper: DatabaseHelper
    lateinit var preferences: SharedPreferences

    /*
     * (non-Javadoc)
     * @see android.app.Service#onCreate()
     */
    override fun onCreate() {
        super.onCreate()
        LogHelper.d(TAG, "create service")

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        databaseHelper = DatabaseHelper(this)

        mMusicProvider = MusicProvider(this)

        mQueueManager = QueueManager(mMusicProvider, resources,
                object : QueueManager.MetadataUpdateListener {
                    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                        mSession.setMetadata(metadata)
                    }

                    override fun onMetadataRetrieveError() {
                        mPlaybackManager.updatePlaybackState(
                                getString(R.string.error_no_metadata))
                    }

                    override fun onCurrentQueueIndexUpdated(queueIndex: Int) {
                        mPlaybackManager.handlePlayRequest()
                    }

                    override fun onQueueUpdated(title: String,
                                                newQueue: List<MediaSessionCompat.QueueItem>) {
                        mSession.setQueue(newQueue)
                        mSession.setQueueTitle(title)
                    }
                }, this)

        mPlaybackManager = PlaybackManager(this, resources, mMusicProvider, mQueueManager,
                LocalPlayback(this, mMusicProvider), preferences)

        // Start a new MediaSession
        mSession = MediaSessionCompat(this, "MusicService")
        sessionToken = mSession.sessionToken
        mSession.setCallback(mPlaybackManager.mediaSessionCallback)
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        mPlaybackManager.updatePlaybackState(null)

        try {
            mMediaNotificationManager = MediaNotificationManager(this)
        } catch (e: RemoteException) {
            throw IllegalStateException("Could not create a MediaNotificationManager", e)
        }

        if (started && mQueueManager.currentMusic == null) {
//            mPlaybackManager.loadLastTrack()
        }
    }

    /**
     * (non-Javadoc)
     *
     * @see android.app.Service.onStartCommand
     */
    override fun onStartCommand(startIntent: Intent?, flags: Int, startId: Int): Int {
        LogHelper.d(TAG, "onStartCommand intent=", startIntent)
        if (startIntent != null) {
            val action = startIntent.action
            val command = startIntent.getStringExtra(CMD_NAME)

            if (ACTION_CMD == action && CMD_PAUSE == command) {
                mPlaybackManager.handlePauseRequest()
            } else {
                // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                MediaButtonReceiver.handleIntent(mSession, startIntent)
            }
        }

        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())

        return Service.START_STICKY
    }


    /*
     * Handle case when user swipes the app away from the recents apps list by
     * stopping the service (and any ongoing playback).
     */
    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    /**
     * (non-Javadoc)
     *
     * @see android.app.Service.onDestroy
     */
    override fun onDestroy() {
        LogHelper.d(TAG, "onDestroy")
        // Service is being killed, so make sure we release our resources
        mPlaybackManager.handleStopRequest(null)
        mMediaNotificationManager.stopNotification()
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mSession.release()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int,
                           rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
        LogHelper.d(TAG, "OnGetRoot: clientPackageName=$clientPackageName",
                "; clientUid=$clientUid ; rootHints=", rootHints)

        return MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_ROOT, null)
    }

    override fun onLoadChildren(parentMediaId: String,
                                result: MediaBrowserServiceCompat.Result<List<MediaItem>>) {
        onLoadChildren(parentMediaId, result, Bundle())
    }

    override fun onLoadChildren(parentMediaId: String,
                                result: MediaBrowserServiceCompat.Result<List<MediaItem>>,
                                options: Bundle) {
        LogHelper.d(TAG, "OnLoadChildren: parentMediaId=", parentMediaId)
        if (mMusicProvider.hasItems(parentMediaId, options) && MusicProvider.OPTION_REFRESH != options.getString(MusicProvider.ACTION)) {
            result.sendResult(mMusicProvider.getChildren(parentMediaId, options))
        } else {
            result.detach()
            // To make the app more responsive, fetch and cache catalog information now.
            // This can help improve the response time in the method
            // {@link #onLoadChildren(String, Result<List<MediaItem>>) onLoadChildren()}.
            mMusicProvider.retrieveMediaAsync(parentMediaId, options, object : MusicProvider.Callback {

                override fun onMusicCatalogReady(success: Boolean) {
                    if (success) {
                        LogHelper.d(TAG, "getChildren parentMediaId=", parentMediaId)
                        if (mQueueManager.currentMusic == null) {
                            try {
                                mPlaybackManager.loadLastTrack()
                            } catch (e: IllegalArgumentException) {
                                LogHelper.w(TAG, e, "can not load last track media id")
                            }
                        }
                        try {
                            result.sendResult(mMusicProvider.getChildren(parentMediaId, options))
                        } catch (e: Exception) {
                            LogHelper.w(TAG, e, "can not send result")
                        }
                    } else {
                        result.sendResult(buildError("error"))
                    }
                }

                override fun onMusicCatalogReady(error: String) {
                    result.sendResult(buildError(error))
                }


                private fun buildError(error: String): List<MediaItem> {
                    val foo = ArrayList<MediaItem>()
                    val builder = MediaDescriptionCompat.Builder()
                    builder.setMediaId("error")
                    builder.setDescription(error)
                    foo.add(MediaItem(builder.build(), -1))
                    return foo
                }
            })
        }
    }

    /**
     * Callback method called from PlaybackManager whenever the music is about to play.
     */
    override fun onPlaybackStart() {
        LogHelper.d(TAG, "onPlaybackStart")
        mSession.isActive = true
        mDelayedStopHandler.removeCallbacksAndMessages(null)

        // The service needs to continue running even after the bound client (usually a
        // MediaController) disconnects, otherwise the music playback will stop.
        // Calling startService(Intent) will keep the service running until it is explicitly killed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, MusicService::class.java))
        } else {
            startService(Intent(this, MusicService::class.java))
        }
    }


    /**
     * Callback method called from PlaybackManager whenever the music stops playing.
     */
    override fun onPlaybackStop() {
        mSession.isActive = false
        // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
        // potentially stopping the service.
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())
        mQueueManager.currentMusic?.description?.mediaId.let {
            preferences.edit().putString("last_media_id", it).apply()
        }
    }

    override fun onNotificationRequired() {
        mMediaNotificationManager.startNotification()
    }

    override fun onPlaybackStateUpdated(newState: PlaybackStateCompat) {
        try {
            mSession.setPlaybackState(newState)
        } catch (e: IllegalStateException) {
            LogHelper.e(TAG, "error while updating playback state", e)
        }

    }

    /**
     * A simple handler that stops the service if playback is not active (playing)
     */
    private class DelayedStopHandler internal constructor(service: MusicService) : Handler() {

        private val mWeakReference: WeakReference<MusicService> = WeakReference(service)

        override fun handleMessage(msg: Message) {
            val service = mWeakReference.get()
            if (service != null) {
                if (service.mPlaybackManager.playback.isPlaying) {
                    LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.")
                    return
                }
                LogHelper.d(TAG, "Stopping service with delay handler.")
                service.stopSelf()
            }
        }
    }

    companion object {

        // The action of the incoming Intent indicating that it contains a command
        // to be executed (see {@link #onStartCommand})
        const val ACTION_CMD = "com.tiefensuche.soundcrowd.ACTION_CMD"
        // The key in the extras of the incoming Intent indicating the command that
        // should be executed (see {@link #onStartCommand})
        const val CMD_NAME = "CMD_NAME"
        // A value of a CMD_NAME key in the extras of the incoming Intent that
        // indicates that the music playback should be paused (see {@link #onStartCommand})
        const val CMD_PAUSE = "CMD_PAUSE"

        private val TAG = LogHelper.makeLogTag(MusicService::class.java)
        // Delay stopSelf by using a handler.
        private const val STOP_DELAY = 600000
        private lateinit var mMusicProvider: MusicProvider
        private var started = false
    }
}
