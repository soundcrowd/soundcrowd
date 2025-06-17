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
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.service.PlaybackService
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_GET_PLUGINS
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.RESULT
import com.tiefensuche.soundcrowd.ui.intro.IntroActivity

/**
 * Base activity for activities that need to show a playback control fragment when media is playing.
 */
abstract class BaseActivity : ActionBarCastActivity(), MediaBrowserProvider {

    private var _mediaBrowser: MediaBrowser? = null
    override val mediaBrowser: MediaBrowser
        get() {
            return _mediaBrowser!!
        }
    override val connected: Boolean
        get() = _mediaBrowser != null
    private var mFullScreenPlayerFragment: FullScreenPlayerFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "Activity onCreate")

        val taskDesc = ActivityManager.TaskDescription(
                title.toString(),
                BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher),
                resources.getColor(R.color.colorPrimary))
        setTaskDescription(taskDesc)

        // Only check if a full screen player is needed on the first time:
        if (savedInstanceState == null) {
            startFullScreenActivityIfNeeded(intent)
        }

        checkPermissions()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "Activity onStart")

        mFullScreenPlayerFragment = supportFragmentManager.findFragmentById(R.id.fragment_fullscreen_player) as? FullScreenPlayerFragment
        if (connected && shouldShowControls()) {
            mFullScreenPlayerFragment?.onMetadataChanged(mediaBrowser.currentMediaItem!!.mediaMetadata, true)
            showPlaybackControls()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startFullScreenActivityIfNeeded(intent)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            handleIntent(intent)
        } else {
            setIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action != null && intent.action!!.endsWith("intent.action.VIEW") && intent.data != null) {
            // handle as file path to a music track
            mediaBrowser.sendCustomCommand(SessionCommand(PlaybackService.COMMAND_RESOLVE, Bundle.EMPTY), Bundle().apply { putParcelable(PlaybackService.ARG_URI, intent.data) })
        }
        intent.action = null
    }

    private fun startFullScreenActivityIfNeeded(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra(MusicPlayerActivity.EXTRA_START_FULLSCREEN, false)) {
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun showPlaybackControls() {
        if (sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private fun hidePlaybackControls() {
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    /**
     * Check if the MediaSession is active and in a "playback-able" state
     * (not NONE and not STOPPED).
     *
     * @return true if the MediaSession's state requires playback controls to be visible.
     */
    private fun shouldShowControls(): Boolean {
        return mediaBrowser.currentMediaItem != null
    }

    private fun getPlugins() {
        val result = mediaBrowser.sendCustomCommand(SessionCommand(COMMAND_GET_PLUGINS, Bundle.EMPTY), Bundle())
        result.addListener({
            updatePlugins(result.get().extras.getParcelableArrayList<Bundle>(RESULT) as List<Bundle>)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun connectMediaBrowser() {
        val sessionToken =
            SessionToken(this, ComponentName(this, PlaybackService::class.java))

        val browserFuture = MediaBrowser.Builder(this, sessionToken).buildAsync()
        browserFuture.addListener({
            _mediaBrowser = browserFuture.get()
            mediaBrowser.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (shouldShowControls()) {
                        showPlaybackControls()
                    } else {
                        Log.d(TAG, "mediaControllerCallback.onPlaybackStateChanged: hiding controls because state is ${playbackState}")
                        hidePlaybackControls()
                    }
                    mFullScreenPlayerFragment?.onPlaybackStateChanged()
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    if (shouldShowControls()) {
                        showPlaybackControls()
                    } else {
                        Log.d(TAG, "mediaControllerCallback.onMetadataChanged: hiding controls because metadata is null")
                        hidePlaybackControls()
                    }
                    mFullScreenPlayerFragment?.onMetadataChanged(mediaMetadata)
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_IS_PLAYING_CHANGED))
                        mFullScreenPlayerFragment?.onPlaybackStateChanged()
                }
            })
            handleIntent(intent)
            getPlugins()
            if (shouldShowControls()) {
                mFullScreenPlayerFragment?.onPlaybackStateChanged()
                mFullScreenPlayerFragment?.onMetadataChanged(mediaBrowser.currentMediaItem!!.mediaMetadata, true)
                showPlaybackControls()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
        } else {
            connectMediaBrowser()
        }
    }

    companion object {
        private val TAG = BaseActivity::class.simpleName
        const val MIME_TEXT = "text/plain"
    }
}