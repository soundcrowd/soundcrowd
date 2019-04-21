/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.playback

import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.database.DatabaseHelper
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.utils.LogHelper

import com.tiefensuche.soundcrowd.utils.MediaIDHelper.extractMusicIDFromMediaID

/**
 * Manage the interactions among the container service, the queue manager and the actual playback.
 */
class PlaybackManager(private val mServiceCallback: PlaybackServiceCallback, private val mResources: Resources,
                      private val mMusicProvider: MusicProvider, private val mQueueManager: com.tiefensuche.soundcrowd.playback.QueueManager,
                      val playback: Playback, private val mPreferences: SharedPreferences) : Playback.Callback {
    private val mMediaSessionCallback: MediaSessionCallback

    val mediaSessionCallback: MediaSessionCompat.Callback
        get() = mMediaSessionCallback

    private val availableActions: Long
        get() {
            var actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            actions = if (playback.isPlaying) {
                actions or PlaybackStateCompat.ACTION_PAUSE
            } else {
                actions or PlaybackStateCompat.ACTION_PLAY
            }
            return actions
        }

    init {
        mMediaSessionCallback = MediaSessionCallback()
        this.playback.setCallback(this)
    }

    fun seek(position: Int) {
        playback.seekTo(position)
    }

    /**
     * Handle a request to play music
     */
    fun handlePlayRequest() {
        LogHelper.d(TAG, "handlePlayRequest: mState=" + playback.state)
        val currentMusic = mQueueManager.currentMusic
        if (currentMusic != null) {
            mServiceCallback.onPlaybackStart()
            playback.play(currentMusic)
        }
    }

    /**
     * Handle a request to play music
     */
    private fun handlePlayRequestAtPosition(position: Int) {
        LogHelper.d(TAG, "handlePlayRequestAtPosition: mState=" + playback.state)
        val currentMusic = mQueueManager.currentMusic
        if (currentMusic != null) {
            mServiceCallback.onPlaybackStart()
            playback.play(currentMusic, position)
        }
    }

    /**
     * Handle a request to pause music
     */
    fun handlePauseRequest() {
        LogHelper.d(TAG, "handlePauseRequest: mState=" + playback.state)
        if (playback.isPlaying) {
            playback.pause()
            mServiceCallback.onPlaybackStop()
        }
    }

    /**
     * Handle a request to stop music
     *
     * @param withError Error message in case the stop has an unexpected cause. The error
     * message will be set in the PlaybackState and will be visible to
     * MediaController clients.
     */
    fun handleStopRequest(withError: String?) {
        LogHelper.d(TAG, "handleStopRequest: mState=" + playback.state + " error=", withError)
        playback.stop(true)
        mServiceCallback.onPlaybackStop()
        updatePlaybackState(withError)
    }


    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    fun updatePlaybackState(error: String?) {
        LogHelper.d(TAG, "updatePlaybackState, playback state=" + playback.state)
        var position = -1
        if (playback.isConnected) {
            position = playback.currentStreamPosition
        }


        val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(availableActions)

        setCustomAction(stateBuilder)
        var state = playback.state

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(error)
            state = PlaybackStateCompat.STATE_ERROR
        }

        stateBuilder.setState(state, position.toLong(), 1.0f, SystemClock.elapsedRealtime())

        // Set the activeQueueItemId if the current index is valid.
        val currentMusic = mQueueManager.currentMusic
        if (currentMusic != null) {
            stateBuilder.setActiveQueueItemId(currentMusic.queueId)
        }

        mServiceCallback.onPlaybackStateUpdated(stateBuilder.build())

        if (state >= PlaybackStateCompat.STATE_PAUSED) {
            mServiceCallback.onNotificationRequired()
        }
    }

    private fun setCustomAction(stateBuilder: PlaybackStateCompat.Builder) {
        val currentMusic = mQueueManager.currentMusic ?: return
        // Set appropriate "Favorite" icon on Custom action:
        val mediaId = currentMusic.description.mediaId ?: return
        val musicId = extractMusicIDFromMediaID(mediaId)
        val favoriteIcon = if (mMusicProvider.isFavorite(musicId))
            R.drawable.ic_star_on
        else
            R.drawable.ic_star_off
        LogHelper.d(TAG, "updatePlaybackState, setting Favorite custom action of music ",
                musicId, " current favorite=", mMusicProvider.isFavorite(musicId))
        val customActionExtras = Bundle()
        //        WearHelper.setShowCustomActionOnWear(customActionExtras, true);
        stateBuilder.addCustomAction(PlaybackStateCompat.CustomAction.Builder(
                CUSTOM_ACTION_THUMBS_UP, mResources.getString(R.string.favorite), favoriteIcon)
                .setExtras(customActionExtras)
                .build())
    }

    /**
     * Implementation of the Playback.Callback interface
     */
    override fun onCompletion() {
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mQueueManager.skipQueuePosition(1)) {
            handlePlayRequest()
            mQueueManager.updateMetadata()
        } else {
            // If skipping was not possible, we stop and release the resources:
            handleStopRequest(null)
        }
    }

    override fun onPlaybackStatusChanged(state: Int) {
        updatePlaybackState(null)
    }

    override fun onError(error: String) {
        updatePlaybackState(error)
    }

    override fun setCurrentMediaId(mediaId: String) {
        LogHelper.d(TAG, "setCurrentMediaId", mediaId)
        mQueueManager.setQueueFromMusic(mediaId)
    }

    private fun updateLastPosition() {
        mQueueManager.currentMusic?.description?.mediaId?.let {
            DatabaseHelper.instance.updatePosition(extractMusicIDFromMediaID(it), playback.currentStreamPosition)
            mPreferences.edit().putString("last_media_id", it).apply()
        }
    }

    fun loadLastTrack() {
        val lastMediaId = mPreferences.getString("last_media_id", null)
        LogHelper.d(TAG, "load metadata for lastMediaId=", lastMediaId)
        if (lastMediaId != null) {
            val musicId = extractMusicIDFromMediaID(lastMediaId)
            mQueueManager.update(musicId)
        }
    }

    private fun playAtPosition(mediaId: String, position: Int) {
        if (mMusicProvider.getMusic(extractMusicIDFromMediaID(mediaId)) != null) {
            setCurrentMediaId(mediaId)
            handlePlayRequestAtPosition(position)
        }
    }

    interface PlaybackServiceCallback {
        fun onPlaybackStart()

        fun onNotificationRequired()

        fun onPlaybackStop()

        fun onPlaybackStateUpdated(newState: PlaybackStateCompat)
    }

    internal inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            LogHelper.d(TAG, "play")
            if (mQueueManager.currentMusic == null) {
                mQueueManager.setRandomQueue()
            }
            handlePlayRequest()
        }

        override fun onSkipToQueueItem(queueId: Long) {
            LogHelper.d(TAG, "OnSkipToQueueItem:$queueId")
            mQueueManager.setCurrentQueueItem(queueId)
            mQueueManager.updateMetadata()
        }

        override fun onSeekTo(position: Long) {
            LogHelper.d(TAG, "onSeekTo:", position)
            playback.seekTo(position.toInt())
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            LogHelper.d(TAG, "playFromMediaId mediaId:", mediaId, "  extras=", extras)
            mQueueManager.currentMusic?.let {
                if (mediaId == it.description.mediaId) {
                    return
                }
                updateLastPosition()
            }
            mQueueManager.setQueueFromMusic(mediaId)
            handlePlayRequest()
        }

        override fun onPlayFromUri(uri: Uri, extras: Bundle?) {
            LogHelper.d(TAG, "playFromUri uri:", uri, "  extras=", extras)
            if (mQueueManager.currentMusic != null) {
                updateLastPosition()
            }
            mQueueManager.setQueueFromMusic(uri)
            handlePlayRequest()
        }

        override fun onPause() {
            LogHelper.d(TAG, "pause. current state=" + playback.state)
            updateLastPosition()
            handlePauseRequest()
        }

        override fun onStop() {
            LogHelper.d(TAG, "stop. current state=" + playback.state)
            updateLastPosition()
            handleStopRequest(null)
        }

        override fun onSkipToNext() {
            LogHelper.d(TAG, "skipToNext")
            updateLastPosition()
            if (mQueueManager.skipQueuePosition(1)) {
                handlePlayRequest()
            } else {
                handleStopRequest("Cannot skip")
            }
            mQueueManager.updateMetadata()
        }

        override fun onSkipToPrevious() {
            updateLastPosition()
            if (mQueueManager.skipQueuePosition(-1)) {
                handlePlayRequest()
            } else {
                handleStopRequest("Cannot skip")
            }
            mQueueManager.updateMetadata()
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when {
                CUSTOM_ACTION_THUMBS_UP == action -> {
                    LogHelper.d(TAG, "onCustomAction: favorite for current track")
                    val currentMusic = mQueueManager.currentMusic
                    if (currentMusic != null) {
                        val mediaId = currentMusic.description.mediaId
                        if (mediaId != null) {
                            val musicId = extractMusicIDFromMediaID(mediaId)
                            mMusicProvider.setFavorite(musicId, !mMusicProvider.isFavorite(musicId))
                        }
                    }
                    // playback state needs to be updated because the "Favorite" icon on the
                    // custom action will change to reflect the new favorite state.
                    updatePlaybackState(null)
                }
                CUSTOM_ACTION_PLAY_SEEK == action -> extras?.getString("mediaId")?.let { playAtPosition(it, extras.getInt("position")) }
                else -> LogHelper.e(TAG, "Unsupported action: ", action)
            }
        }
    }

    companion object {

        // Action to thumbs up a media item
        private const val CUSTOM_ACTION_THUMBS_UP = "com.tiefensuche.soundcrowd.THUMBS_UP"
        const val CUSTOM_ACTION_PLAY_SEEK = "com.tiefensuche.soundcrowd.PLAY_SEEK"
        private val TAG = LogHelper.makeLogTag(PlaybackManager::class.java)
    }
}
