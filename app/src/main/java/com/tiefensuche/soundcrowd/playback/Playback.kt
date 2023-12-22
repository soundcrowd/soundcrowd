/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.playback

import android.support.v4.media.session.MediaSessionCompat.QueueItem

/**
 * Interface representing either Local or Remote Playback. The [com.tiefensuche.soundcrowd.service.MusicService] works
 * directly with an instance of the Playback object to make the various calls such as
 * play, pause etc.
 */
interface Playback {

    /**
     * @return boolean that indicates that this is ready to be used.
     */
    val isConnected: Boolean

    /**
     * @return boolean indicating whether the player is playing or is supposed to be
     * playing when we gain audio focus.
     */
    val isPlaying: Boolean

    /**
     * @return pos if currently playing an item
     */
    val currentStreamPosition: Long

    var currentMediaId: String

    /**
     * Start/setup the playback.
     * Resources/listeners would be allocated by implementations.
     */
    fun start()

    /**
     * Stop the playback. All resources can be de-allocated by implementations here.
     *
     * @param notifyListeners if true and a callback has been set by setCallback,
     * callback.onPlaybackStatusChanged will be called after changing
     * the state.
     */
    fun stop(notifyListeners: Boolean)

    /**
     * Get the current {@link android.media.session.PlaybackState#getState()}
     */
    fun getState(): Int

    /**
     * Queries the underlying stream and update the internal last known stream position.
     */
    fun updateLastKnownStreamPosition()

    fun play(item: QueueItem)

    fun play(item: QueueItem, position: Long)

    fun pause()

    fun seekTo(position: Long)

    fun setCallback(callback: Callback)

    interface Callback {
        /**
         * On current music completed.
         */
        fun onCompletion()

        /**
         * on Playback status changed
         * Implementations can use this callback to update
         * playback state on the media sessions.
         */
        fun onPlaybackStatusChanged(state: Int)

        /**
         * @param error to be added to the PlaybackState
         */
        fun onError(error: String)

        /**
         * @param mediaId being currently played
         */
        fun setCurrentMediaId(mediaId: String)
    }
}