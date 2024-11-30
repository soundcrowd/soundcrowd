/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.service.MusicService.Companion.ARG_URL
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.RESULT
import com.tiefensuche.soundcrowd.ui.intro.IntroActivity
import com.tiefensuche.soundcrowd.ui.intro.ShowcaseViewManager

/**
 * Base activity for activities that need to show a playback control fragment when media is playing.
 */
abstract class BaseActivity : ActionBarCastActivity(), MediaBrowserProvider {

    // Callback that ensures that we are showing the controls
    private val mMediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            if (shouldShowControls()) {
                showPlaybackControls()
            } else {
                Log.d(TAG, "mediaControllerCallback.onPlaybackStateChanged: hiding controls because state is ${state?.state}")
                hidePlaybackControls()
            }
            mFullScreenPlayerFragment?.onPlaybackStateChanged(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            if (shouldShowControls()) {
                showPlaybackControls()
            } else {
                Log.d(TAG, "mediaControllerCallback.onMetadataChanged: hiding controls because metadata is null")
                hidePlaybackControls()
            }
            mFullScreenPlayerFragment?.onMetadataChanged(metadata)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            mFullScreenPlayerFragment?.updateRepeatMode(repeatMode)
        }

        override fun onShuffleModeChanged(shuffleMode: Int) {
            mFullScreenPlayerFragment?.updateShuffleMode(shuffleMode)
        }
    }

    override lateinit var mediaBrowser: MediaBrowserCompat
    private var mFullScreenPlayerFragment: FullScreenPlayerFragment? = null
    private val mConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            getPlugins()
            try {
                connectToSession(mediaBrowser.sessionToken)
                onMediaControllerConnected()
                handleIntent(intent)
            } catch (e: RemoteException) {
                Log.e(TAG, "could not connect media controller", e)
                hidePlaybackControls()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "Activity onCreate")

        if (Build.VERSION.SDK_INT >= 21) {
            // Since our app icon has the same color as colorPrimary, our entry in the Recent Apps
            // list gets weird. We need to change either the icon or the color
            // of the TaskDescription.
            val taskDesc = ActivityManager.TaskDescription(
                    title.toString(),
                    BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher),
                    resources.getColor(R.color.colorPrimary))
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
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "Activity onStart")

        mFullScreenPlayerFragment = supportFragmentManager.findFragmentById(R.id.fragment_fullscreen_player) as? FullScreenPlayerFragment
        checkPermissions()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "Activity onStop")
        if (MediaControllerCompat.getMediaController(this) != null) {
            MediaControllerCompat.getMediaController(this).unregisterCallback(mMediaControllerCallback)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaBrowser.disconnect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startFullScreenActivityIfNeeded(intent)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            handleIntent(intent)
        } else {
            setIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action != null && intent.action!!.endsWith("intent.action.VIEW") && intent.data != null) {
            // handle as file path to a music track
            val service = Intent(this, MusicService::class.java)
            service.action = MusicService.ACTION_CMD
            service.putExtra(MusicService.CMD_NAME, MusicService.CMD_RESOLVE)
            service.putExtra(ARG_URL, intent.data)
            startService(service)
        }
        intent.action = null
    }

    private fun startFullScreenActivityIfNeeded(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra(MusicPlayerActivity.EXTRA_START_FULLSCREEN, false)) {
            showPlaybackControls()
            slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
        }
    }

    private fun showPlaybackControls() {
        if (slidingUpPanelLayout.panelState == SlidingUpPanelLayout.PanelState.HIDDEN) {
            slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
        }
        ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.SLIDING_UP, this)
    }

    private fun hidePlaybackControls() {
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
        mediaBrowser.sendCustomAction(MusicProvider.ACTION_GET_PLUGINS, Bundle(), object : MediaBrowserCompat.CustomActionCallback() {
            override fun onResult(action: String, extras: Bundle, resultData: Bundle) {
                updatePlugins(resultData.getParcelableArrayList(RESULT)!!)
            }
        })
    }

    private fun connectMediaBrowser() {
        if (!mediaBrowser.isConnected) {
            mediaBrowser.connect()
        } else {
            connectToSession(mediaBrowser.sessionToken)
            // update metadata and playback state in case of reconnecting to a running session
            // when returning to the activity
            val mediaController = MediaControllerCompat.getMediaController(this)
            if (mediaController.metadata != null) {
                mMediaControllerCallback.onMetadataChanged(mediaController.metadata)
                mMediaControllerCallback.onPlaybackStateChanged(mediaController.playbackState)
                handleIntent(intent)
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, IntroActivity::class.java))
        } else {
            connectMediaBrowser()
        }
    }

    companion object {
        private val TAG = BaseActivity::class.simpleName
        const val MIME_TEXT = "text/plain"
    }
}