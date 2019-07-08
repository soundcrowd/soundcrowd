/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.ui.intro.ShowcaseViewManager
import com.tiefensuche.soundcrowd.utils.LogHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper
import com.tiefensuche.soundcrowd.utils.Utils

/**
 * Base activity for activities that need to show a playback control fragment when media is playing.
 */
abstract class BaseActivity : ActionBarCastActivity(), MediaBrowserProvider {

    companion object {

        private val TAG = LogHelper.makeLogTag(BaseActivity::class.java)
    }

    // Callback that ensures that we are showing the controls
    private val mMediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            if (shouldShowControls()) {
                showPlaybackControls()
            } else {
                LogHelper.d(TAG, "mediaControllerCallback.onPlaybackStateChanged: " + "hiding controls because state is ", state?.state)
                hidePlaybackControls()
            }
            mFullScreenPlayerFragment?.onPlaybackStateChanged(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            if (shouldShowControls()) {
                showPlaybackControls()
            } else {
                LogHelper.d(TAG, "mediaControllerCallback.onMetadataChanged: " + "hiding controls because metadata is null")
                hidePlaybackControls()
            }
            mFullScreenPlayerFragment?.onMetadataChanged(metadata)
        }
    }

    override lateinit var mediaBrowser: MediaBrowserCompat
    private var mFullScreenPlayerFragment: FullScreenPlayerFragment? = null
    private val mConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            LogHelper.d(TAG, "onConnected")
            getPlugins()
            try {
                connectToSession(mediaBrowser.sessionToken)
                onMediaControllerConnected()
            } catch (e: RemoteException) {
                LogHelper.e(TAG, e, "could not connect media controller")
                hidePlaybackControls()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LogHelper.d(TAG, "Activity onCreate")

        if (Build.VERSION.SDK_INT >= 21) {
            // Since our app icon has the same color as colorPrimary, our entry in the Recent Apps
            // list gets weird. We need to change either the icon or the color
            // of the TaskDescription.
            val taskDesc = ActivityManager.TaskDescription(
                    title.toString(),
                    BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher),
                    Utils.getThemeColor(this, R.attr.colorPrimary,
                            android.R.color.darker_gray))
            setTaskDescription(taskDesc)
        }

        // Connect a media browser just to get the media session token. There are other ways
        // this can be done, for example by sharing the session token directly.
        mediaBrowser = MediaBrowserCompat(this,
                ComponentName(this, MusicService::class.java), mConnectionCallback, null)

        // Only check if a full screen player is needed on the first time:
        if (savedInstanceState == null) {
            startFullScreenActivityIfNeeded(intent)
        }
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        LogHelper.d(TAG, "Activity onStart")

        mFullScreenPlayerFragment = supportFragmentManager.findFragmentById(R.id.fragment_fullscreen_player) as? FullScreenPlayerFragment

        LogHelper.d(TAG, "MediaBrowser connected=", mediaBrowser.isConnected)
        if (!mediaBrowser.isConnected) {
            mediaBrowser.connect()
        } else {
            connectToSession(mediaBrowser.sessionToken)
        }
    }

    override fun onStop() {
        super.onStop()
        LogHelper.d(TAG, "Activity onStop")
        if (MediaControllerCompat.getMediaController(this) != null) {
            MediaControllerCompat.getMediaController(this).unregisterCallback(mMediaControllerCallback)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaBrowser.disconnect()
    }

    override fun onNewIntent(intent: Intent) {
        LogHelper.d(TAG, "onNewIntent, intent=$intent")
        startFullScreenActivityIfNeeded(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && "text/plain" == type) { //  && mCurrentMetadata != null
            // handle text via share action, that should be a description for a new cue point
            LogHelper.d(TAG, "add cue point from share action")
            mFullScreenPlayerFragment?.addCuePoint(intent.getStringExtra(Intent.EXTRA_TEXT))
        } else if (Intent.ACTION_VIEW == action && intent.data != null) {
            // handle as file path to a music track, either local or remote (https:// or soundcrowd://)
            val service = Intent(this, MusicService::class.java)
            service.action = MusicService.ACTION_CMD
            service.putExtra(MusicService.CMD_NAME, MusicService.CMD_RESOLVE)
            service.putExtra("url", intent.data)
            startService(service)
        }
    }

    private fun startFullScreenActivityIfNeeded(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra(MusicPlayerActivity.EXTRA_START_FULLSCREEN, false)) {
            showPlaybackControls()
            slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
        }
    }

    private fun showPlaybackControls() {
        LogHelper.d(TAG, "showPlaybackControls")
        if (slidingUpPanelLayout.panelState == SlidingUpPanelLayout.PanelState.HIDDEN) {
            slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        }
        ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.SLIDING_UP, this)
    }

    private fun hidePlaybackControls() {
        LogHelper.d(TAG, "hidePlaybackControls")
        slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.HIDDEN
    }

    internal open fun onMediaControllerConnected() {
        mFullScreenPlayerFragment?.onMediaControllerConnected()
    }

    @Throws(RemoteException::class)
    private fun connectToSession(token: MediaSessionCompat.Token) {
        val mediaController = MediaControllerCompat(this, token)
        MediaControllerCompat.setMediaController(this, mediaController)
        mediaController.registerCallback(mMediaControllerCallback)

        if (shouldShowControls()) {
            showPlaybackControls()
        }
    }

    /**
     * Check if the MediaSession is active and in a "playback-able" state
     * (not NONE and not STOPPED).
     *
     * @return true if the MediaSession's state requires playback controls to be visible.
     */
    private fun shouldShowControls(): Boolean {
        val mediaController = MediaControllerCompat.getMediaController(this)
        return mediaController != null && mediaController.metadata != null
    }

    private fun getPlugins() {
        if (!plugins.isNullOrEmpty()) {
            return
        }
        mediaBrowser.subscribe(MediaIDHelper.MEDIA_ID_PLUGINS, Bundle(), object: MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String,
                                          children: List<MediaBrowserCompat.MediaItem>,
                                          options: Bundle) {
                updatePlugins(children)
            }
        })
    }
}
