/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaDataSource
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player.Listener
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.extensions.UrlResolver
import com.tiefensuche.soundcrowd.plugins.Callback
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.service.PluginManager
import com.tiefensuche.soundcrowd.sources.MusicProvider


/**
 * A class that implements local media playback using [android.media.MediaPlayer]
 */
internal class LocalPlayback(private val mContext: Context, private val mMusicProvider: MusicProvider) : Playback, AudioManager.OnAudioFocusChangeListener, Listener {

    // Create the Wifi lock (this does not acquire the lock, this just creates it)
    private val mWifiLock: WifiManager.WifiLock = (mContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.createWifiLock(WifiManager.WIFI_MODE_FULL, "soundcrowd_lock")
            ?: throw IllegalStateException("can not get wifi service")
    private val mAudioManager: AudioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: throw IllegalStateException("can not get audio service")
    private val mAudioNoisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

    private var mPlayOnFocusGain: Boolean = false
    private val mAudioNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                Log.d(TAG, "Headphones disconnected.")
                if (isPlaying) {
                    val i = Intent(context, MusicService::class.java)
                    i.action = MusicService.ACTION_CMD
                    i.putExtra(MusicService.CMD_NAME, MusicService.CMD_PAUSE)
                    mContext.startService(i)
                }
            }
        }
    }
    private var mCallback: Playback.Callback? = null

    @Volatile
    private var mAudioNoisyReceiverRegistered: Boolean = false

    @Volatile
    private var mCurrentPosition: Long = 0

    @Volatile
    override var currentMediaId: String = ""

    // Type of audio focus we have:
    private var mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK

    private var urlResolver: UrlResolver = object : UrlResolver {
        override fun getMediaUrl(metadata: MediaMetadataCompat, callback: Callback<Pair<MediaMetadataCompat, MediaDataSource?>>) {
            callback.onResult(Pair(metadata, null))
        }
    }

    override val isConnected: Boolean
        get() = true

    override val isPlaying: Boolean
        get() = mPlayOnFocusGain || mMediaPlayer?.playWhenReady ?: false

    override val currentStreamPosition: Long
        get() = mMediaPlayer?.currentPosition ?: mCurrentPosition

    init {
        PluginManager.loadPlugin<UrlResolver>(mContext,
                "com.tiefensuche.soundcrowd.plugins.cache",
                "Extension")?.let { this.urlResolver = it }
    }

    override fun start() {}

    override fun stop(notifyListeners: Boolean) {
        mCurrentPosition = currentStreamPosition
        giveUpAudioFocus()
        unregisterAudioNoisyReceiver()
        relaxResources(true)
    }

    override fun getState(): Int {
        return mMediaPlayer?.let {
            when (it.playbackState) {
                ExoPlayer.STATE_IDLE -> PlaybackStateCompat.STATE_PAUSED
                ExoPlayer.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
                ExoPlayer.STATE_READY -> if (it.playWhenReady) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
                ExoPlayer.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
                else -> PlaybackStateCompat.STATE_NONE
            }
        } ?: PlaybackStateCompat.STATE_NONE
    }

    override fun updateLastKnownStreamPosition() {
        mMediaPlayer?.let {
            mCurrentPosition = it.currentPosition
        }
    }

    override fun play(item: QueueItem) {
        play(item, 0)
    }

    override fun play(item: QueueItem, position: Long) {
        val mediaId = item.description.mediaId ?: return
        mPlayOnFocusGain = true
        tryToGetAudioFocus()
        registerAudioNoisyReceiver()

        val mediaHasChanged = !TextUtils.equals(mediaId, currentMediaId)
        if (mediaHasChanged) {
            currentMediaId = mediaId
        }
        if (position >= 0) {
            mCurrentPosition = position
        }

        if (getState() == PlaybackStateCompat.STATE_PAUSED && !mediaHasChanged && mMediaPlayer != null) {
            configMediaPlayerState()
        } else {
            relaxResources(false) // release everything except MediaPlayer
            mMusicProvider.resolveMusic(mediaId, object : Callback<Pair<MediaMetadataCompat, MediaDataSource?>> {
                override fun onResult(result: Pair<MediaMetadataCompat, MediaDataSource?>) {
                    val clb = object : Callback<Pair<MediaMetadataCompat, MediaDataSource?>> {
                        override fun onResult(result: Pair<MediaMetadataCompat, MediaDataSource?>) {
                            try {
                                createMediaPlayerIfNeeded()

                                mMediaPlayer?.let {
                                    result.second?.let { mediaDataSource ->
                                        val uri =
                                            Uri.parse(result.first.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI))
                                        val dataSource = MediaDataSourceWrapper(mediaDataSource)
                                        dataSource.open(DataSpec(uri))
                                        val factory = DataSource.Factory {
                                            dataSource
                                        }
                                        val mediaSource = ProgressiveMediaSource.Factory(factory)
                                            .createMediaSource(MediaItem.fromUri(uri))
                                        it.setMediaSource(mediaSource)
                                    } ?: run {
                                        it.setMediaItem(
                                            MediaItem.fromUri(
                                                if (result.first.containsKey(MediaMetadataCompatExt.METADATA_KEY_DOWNLOAD_URL)) {
                                                    result.first.getString(MediaMetadataCompatExt.METADATA_KEY_DOWNLOAD_URL)
                                                } else {
                                                    result.first.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)
                                                }
                                            )
                                        )
                                    }

                                    // Starts preparing the media player in the background. When
                                    // it's done, it will call our OnPreparedListener (that is,
                                    // the onPrepared() method on this class, since we set the
                                    // listener to 'this'). Until the media player is prepared,
                                    // we *cannot* call start() on it!
                                    it.prepare()
                                    configMediaPlayerState()

                                    // If we are streaming from the internet, we want to hold a
                                    // Wifi lock, which prevents the Wifi radio from going to
                                    // sleep while the song is playing.
                                    mWifiLock.acquire()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception playing song", e)
                                mCallback?.onError(e.message ?: "")
                            }
                        }
                    }
                    if (result.second == null)
                        urlResolver.getMediaUrl(result.first, clb)
                    else
                        clb.onResult(result)
                }
            })
        }
    }

    override fun pause() {
        // Pause player and cancel the 'foreground service' state.
        mMediaPlayer?.let {
            it.playWhenReady = false
            mCurrentPosition = it.currentPosition
        }
        // While paused, retain the player instance, but give up audio focus.
        relaxResources(false)
        unregisterAudioNoisyReceiver()
    }

    override fun seekTo(position: Long) {
        if (mMediaPlayer == null) {
            // If we do not have a current media player, simply update the current position
            mCurrentPosition = position
        } else {
            registerAudioNoisyReceiver()
            mMediaPlayer?.seekTo(position)
        }
    }

    override fun setCallback(callback: Playback.Callback) {
        this.mCallback = callback
    }

    /**
     * Try to get the system audio focus.
     */
    private fun tryToGetAudioFocus() {
        val result = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)
        mAudioFocus = if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            AUDIO_FOCUSED
        } else {
            AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    /**
     * Give up the audio focus.
     */
    private fun giveUpAudioFocus() {
        if (mAudioManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    /**
     * Reconfigures MediaPlayer according to audio focus settings and
     * starts/restarts it. This method starts/restarts the MediaPlayer
     * respecting the current audio focus state. So if we have focus, it will
     * play normally; if we don't have focus, it will either leave the
     * MediaPlayer paused or set it to a low volume, depending on what is
     * allowed by the current focus settings. This method assumes mPlayer !=
     * null, so if you are calling it, you have to do so from a context where
     * you are sure this is the case.
     */
    private fun configMediaPlayerState() {
        if (mAudioFocus == AUDIO_NO_FOCUS_NO_DUCK) {
            // If we don't have audio focus and can't duck, we have to pause,
            pause()
        } else {  // we have audio focus:
            registerAudioNoisyReceiver()
            if (mAudioFocus == AUDIO_NO_FOCUS_CAN_DUCK) {
                mMediaPlayer?.volume = VOLUME_DUCK // we'll be relatively quiet
            } else {
                mMediaPlayer?.volume = VOLUME_NORMAL // we can be loud again
            }
            // If we were playing when we lost focus, we need to resume playing.
            if (mPlayOnFocusGain) {
                mMediaPlayer?.let {
                    if (mCurrentPosition != it.currentPosition) {
                        it.seekTo(mCurrentPosition)
                    }
                    it.playWhenReady = true
                }
                mPlayOnFocusGain = false
            }
        }
    }

    /**
     * Called by AudioManager on audio focus changes.
     * Implementation of [android.media.AudioManager.OnAudioFocusChangeListener]
     */
    override fun onAudioFocusChange(focusChange: Int) {
        Log.d(TAG, "onAudioFocusChange. focusChange=$focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> mAudioFocus = AUDIO_FOCUSED
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                // Audio focus was lost, but it's possible to duck (i.e.: play quietly)
                mAudioFocus = AUDIO_NO_FOCUS_CAN_DUCK

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Lost audio focus, but will gain it back (shortly), so note whether
                // playback should resume
                mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK
                mPlayOnFocusGain = mMediaPlayer?.playWhenReady ?: false
            }

            AudioManager.AUDIOFOCUS_LOSS ->
                // Lost audio focus, probably "permanently"
                mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK
        }

        if (mMediaPlayer != null) {
            // Update the player state based on the change
            configMediaPlayerState()
        }
    }

    /**
     * Makes sure the media player exists and has been reset. This will create
     * the media player if needed, or reset the existing media player if one
     * already exists.
     */
    private fun createMediaPlayerIfNeeded() {
        Log.d(TAG, "createMediaPlayerIfNeeded. needed? ${mMediaPlayer == null}")
        if (mMediaPlayer == null) {
            mMediaPlayer = ExoPlayer.Builder(mContext).build()
            mMediaPlayer?.let {

                EqualizerControl.setupEqualizerFX(it.audioSessionId, mContext)

                // Make sure the media player will acquire a wake-lock while
                // playing. If we don't do that, the CPU might go to sleep while the
                // song is playing, causing playback to stop.
                it.setWakeMode(PowerManager.PARTIAL_WAKE_LOCK)

                // we want the media player to notify us when it's ready preparing,
                // and when it's done playing:
                it.addListener(this)
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        updatePlaybackState()
        super.onPlayWhenReadyChanged(playWhenReady, reason)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updatePlaybackState()
        super.onPlaybackStateChanged(playbackState)
    }

    private fun updatePlaybackState() {
        when (mMediaPlayer?.playbackState) {
            ExoPlayer.STATE_IDLE,
            ExoPlayer.STATE_BUFFERING,
            ExoPlayer.STATE_READY -> mCallback?.onPlaybackStatusChanged(getState())
            ExoPlayer.STATE_ENDED -> mCallback?.onCompletion()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "Media player error: ${error.message}")
        mCallback?.onError(error.message.toString())
        super.onPlayerError(error)
    }

    /**
     * Releases resources used by the service for playback. This includes the
     * "foreground service" status, the wake locks and possibly the MediaPlayer.
     *
     * @param releaseMediaPlayer Indicates whether the Media Player should also
     * be released or not
     */
    private fun relaxResources(releaseMediaPlayer: Boolean) {
        Log.d(TAG, "relaxResources. releaseMediaPlayer=$releaseMediaPlayer")

        // stop and release the Media Player, if it's available
        if (releaseMediaPlayer) {
            mMediaPlayer?.let {
                it.release()
                mMediaPlayer = null
            }
        }

        if (mWifiLock.isHeld) {
            mWifiLock.release()
        }
    }

    private fun registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mContext.registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter)
            mAudioNoisyReceiverRegistered = true
        }
    }

    private fun unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mContext.unregisterReceiver(mAudioNoisyReceiver)
            mAudioNoisyReceiverRegistered = false
        }
    }

    companion object {

        // The volume we set the media player to when we lose audio focus, but are
        // allowed to reduce the volume instead of stopping playback.
        private const val VOLUME_DUCK = 0.2f

        // The volume we set the media player when we have audio focus.
        private const val VOLUME_NORMAL = 1.0f
        private val TAG = LocalPlayback::class.simpleName

        // we don't have audio focus, and can't duck (play at a low volume)
        private const val AUDIO_NO_FOCUS_NO_DUCK = 0

        // we don't have focus, but can duck (play at a low volume)
        private const val AUDIO_NO_FOCUS_CAN_DUCK = 1

        // we have full audio focus
        private const val AUDIO_FOCUSED = 2
        private var mMediaPlayer: ExoPlayer? = null
    }
}