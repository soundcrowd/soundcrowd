/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.app.ActivityManager
import android.content.ComponentName
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
import com.tiefensuche.soundcrowd.utils.Utils

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
                LogHelper.d(TAG, "mediaControllerCallback.onPlaybackStateChanged: " + "hiding controls because state is ", state?.state)
                hidePlaybackControls()
            }
            mControlsFragment?.onPlaybackStateChanged(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            if (shouldShowControls()) {
                showPlaybackControls()
            } else {
                LogHelper.d(TAG, "mediaControllerCallback.onMetadataChanged: " + "hiding controls because metadata is null")
                hidePlaybackControls()
            }
            mControlsFragment?.onMetadataChanged(metadata)
        }
    }

    override lateinit var mediaBrowser: MediaBrowserCompat
    private var mControlsFragment: FullScreenPlayerFragment? = null
    private val mConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            LogHelper.d(TAG, "onConnected")
            try {
                connectToSession(mediaBrowser.sessionToken)
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
    }

    override fun onStart() {
        super.onStart()
        LogHelper.d(TAG, "Activity onStart")

        mControlsFragment = supportFragmentManager.findFragmentById(R.id.fragment_playback_controls) as FullScreenPlayerFragment?

        mediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()
        LogHelper.d(TAG, "Activity onStop")
        if (MediaControllerCompat.getMediaController(this) != null) {
            MediaControllerCompat.getMediaController(this).unregisterCallback(mMediaControllerCallback)
        }
        mediaBrowser.disconnect()
    }

    internal fun showPlaybackControls() {
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
        mControlsFragment?.onMediaControllerConnected()
    }

    @Throws(RemoteException::class)
    private fun connectToSession(token: MediaSessionCompat.Token) {
        val mediaController = MediaControllerCompat(this, token)
        MediaControllerCompat.setMediaController(this, mediaController)
        mediaController.registerCallback(mMediaControllerCallback)

        if (shouldShowControls()) {
            showPlaybackControls()
        }

        onMediaControllerConnected()
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

    companion object {

        private val TAG = LogHelper.makeLogTag(BaseActivity::class.java)
    }

}
