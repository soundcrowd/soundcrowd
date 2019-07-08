/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.playback

import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.database.Database
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.plugins.Callback
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.extractMusicIDFromMediaID

/**
 * Manage the interactions among the container service, the queue manager and the actual playback.
 */
internal class PlaybackManager(private val mServiceCallback: PlaybackServiceCallback, private val mResources: Resources,
                               private val mMusicProvider: MusicProvider, private val mQueueManager: QueueManager,
                               val playback: Playback, private val mPreferences: SharedPreferences) : Playback.Callback {

    private val mMediaSessionCallback: MediaSessionCallback

    internal val mediaSessionCallback: MediaSessionCompat.Callback
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

    /**
     * Handle a request to play music
     */
    internal fun handlePlayRequest() {
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
        val currentMusic = mQueueManager.currentMusic
        if (currentMusic != null) {
            mServiceCallback.onPlaybackStart()
            playback.play(currentMusic, position)
        }
    }

    /**
     * Handle a request to pause music
     */
    internal fun handlePauseRequest() {
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
    internal fun handleStopRequest(withError: String?) {
        playback.stop(true)
        mServiceCallback.onPlaybackStop()
        updatePlaybackState(withError)
    }

    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    internal fun updatePlaybackState(error: String?) {
        Log.d(TAG, "updatePlaybackState, playback state=${playback.state}")
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
        val customActionExtras = Bundle()
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
        mQueueManager.setQueueFromMusic(mediaId)
    }

    internal fun updateLastPosition() {
        mQueueManager.currentMusic?.description?.mediaId?.let {
            val musicId = extractMusicIDFromMediaID(it)
            mMusicProvider.getMusic(musicId)?.let {
                Database.instance.updatePosition(it, playback.currentStreamPosition)
                mPreferences.edit().putString("last_media_id", musicId).apply()
            }
        }
    }

    internal fun loadLastTrack() {
        val lastMediaId = mPreferences.getString("last_media_id", null)
        if (lastMediaId != null) {
            if (mMusicProvider.loadLastMedia(lastMediaId)) {
                mQueueManager.update(lastMediaId)
            }
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
            if (mQueueManager.currentMusic == null && !mQueueManager.setLastItem()) {
                return
            }
            handlePlayRequest()
        }

        override fun onSkipToQueueItem(queueId: Long) {
            mQueueManager.setCurrentQueueItem(queueId)
            mQueueManager.updateMetadata()
        }

        override fun onSeekTo(position: Long) {
            playback.seekTo(position.toInt())
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
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
            if (mQueueManager.currentMusic != null) {
                updateLastPosition()
            }
            mQueueManager.setQueueFromMusic(uri)
            handlePlayRequest()
        }

        override fun onPause() {
            updateLastPosition()
            handlePauseRequest()
        }

        override fun onStop() {
            updateLastPosition()
            handleStopRequest(null)
        }

        override fun onSkipToNext() {
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
            when (action) {
                CUSTOM_ACTION_THUMBS_UP -> {
                    Log.d(TAG, "onCustomAction: favorite for current track")
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
                CUSTOM_ACTION_PLAY_SEEK -> extras?.getString("mediaId")?.let { playAtPosition(it, extras.getInt("position")) }
                else -> Log.e(TAG, "Unsupported action: $action")
            }
        }

        override fun onSetRating(rating: RatingCompat) {
            Log.d(TAG, "onSetRating. hasHeart=" + rating.hasHeart())

            mQueueManager.currentMusic?.description?.mediaId?.let {
                val metadata = mMusicProvider.getMusic(extractMusicIDFromMediaID(it)) ?: return
                AsyncTask.execute {
                    mMusicProvider.favorite(metadata, object : Callback<Boolean> {
                        override fun onResult(result: Boolean) {
                            if (result) {
                                triggerUpdate(metadata, rating)
                            } else {
                                Log.d(TAG, "failed to favorite track")
                            }
                        }
                    })
                }
            }
        }

        private fun triggerUpdate(metadata: MediaMetadataCompat, rating: RatingCompat) {
            val builder = MediaMetadataCompat.Builder(metadata)
                    .putRating(MediaMetadataCompatExt.METADATA_KEY_FAVORITE, rating)
            mQueueManager.mListener.onMetadataChanged(builder.build())
            updatePlaybackState(null)
        }
    }

    companion object {

        // Action to thumbs up a media item
        private const val CUSTOM_ACTION_THUMBS_UP = "com.tiefensuche.soundcrowd.THUMBS_UP"
        const val CUSTOM_ACTION_PLAY_SEEK = "com.tiefensuche.soundcrowd.PLAY_SEEK"
        private val TAG = PlaybackManager::class.simpleName
    }
}