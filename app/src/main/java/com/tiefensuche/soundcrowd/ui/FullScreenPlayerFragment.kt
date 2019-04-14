/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.database.DatabaseHelper
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.ui.intro.ShowcaseViewManager
import com.tiefensuche.soundcrowd.utils.LogHelper
import com.tiefensuche.soundcrowd.utils.Utils
import com.tiefensuche.soundcrowd.waveform.WaveformHandler
import com.tiefensuche.soundcrowd.waveform.WaveformView
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * A full screen player that shows the current playing music with a background image
 * depicting the album art. The activity also has controls to seek/pause/play the audio.
 */
class FullScreenPlayerFragment : Fragment() {
    private val mHandler = Handler()
    private val mExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var mLastPlaybackState: PlaybackStateCompat? = null
    private var currentPosition: Int = 0
    private lateinit var mControllers: View
    private var mCurrentMetadata: MediaMetadataCompat? = null
    private lateinit var waveformView: WaveformView
    private var duration: Int = 0
    private val mUpdateWaveformProgressTask = Runnable { this.updateWaveformProgress() }
    private var requests: GlideRequests? = null
    private lateinit var mTitle: TextView
    private lateinit var mSubtitle: TextView
    private lateinit var mAlbumArt: ImageView
    private lateinit var mPlayPauseSmall: ImageButton
    private lateinit var mSkipPrev: ImageView
    private lateinit var mSkipNext: ImageView
    private lateinit var mPlayPause: ImageView
    private lateinit var mStart: TextView
    private lateinit var mEnd: TextView
    private lateinit var mSeekbar: SeekBar
    private val mUpdateProgressTask = Runnable { this.updateProgress() }
    private lateinit var mLine1: TextView
    private lateinit var mLine2: TextView
    private lateinit var mLine3: TextView
    private lateinit var mLoading: ProgressBar
    private var mPauseDrawable: Drawable? = null
    private var mPlayDrawable: Drawable? = null
    private lateinit var mBackgroundImage: ImageView
    private lateinit var waveformHandler: WaveformHandler
    private var vibrantColor: Int = 0
    private var textColor: Int = 0
    private var mScheduleFuture: ScheduledFuture<*>? = null
    private var mScheduleFutureWaveform: ScheduledFuture<*>? = null


    private val mButtonListener = View.OnClickListener { view ->
        val controller = MediaControllerCompat.getMediaController(activity!!)
        val stateObj = controller.playbackState
        val state = stateObj?.state ?: PlaybackStateCompat.STATE_NONE
        LogHelper.d(TAG, "Button pressed, in state $state")
        when (view.id) {
            R.id.play_pause -> {
                LogHelper.d(TAG, "Play button pressed, in state $state")
                if (state == PlaybackStateCompat.STATE_PAUSED ||
                        state == PlaybackStateCompat.STATE_STOPPED ||
                        state == PlaybackStateCompat.STATE_NONE) {
                    playMedia()
                } else if (state == PlaybackStateCompat.STATE_PLAYING ||
                        state == PlaybackStateCompat.STATE_BUFFERING ||
                        state == PlaybackStateCompat.STATE_CONNECTING) {
                    pauseMedia()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.playback_controls, container, false)
        mPlayPauseSmall = rootView.findViewById(R.id.play_pause)
        mPlayPauseSmall.isEnabled = true
        mPlayPauseSmall.setOnClickListener(mButtonListener)

        mTitle = rootView.findViewById(R.id.title)
        mSubtitle = rootView.findViewById(R.id.artist)
        mAlbumArt = rootView.findViewById(R.id.album_art)

        requests = GlideApp.with(this)

        mBackgroundImage = rootView.findViewById(R.id.background_image)
        mPauseDrawable = ContextCompat.getDrawable(rootView.context, R.drawable.ic_pause_white_48dp)
        mPlayDrawable = ContextCompat.getDrawable(rootView.context, R.drawable.ic_play_arrow_white_48dp)
        mPlayPause = rootView.findViewById(R.id.play_pause_fullscreen)
        mSkipNext = rootView.findViewById(R.id.next)
        mSkipPrev = rootView.findViewById(R.id.prev)
        mStart = rootView.findViewById(R.id.startText)
        mEnd = rootView.findViewById(R.id.endText)
        mSeekbar = rootView.findViewById(R.id.seekBar1)
        mLine1 = rootView.findViewById(R.id.line1)
        mLine2 = rootView.findViewById(R.id.line2)
        mLine3 = rootView.findViewById(R.id.line3)
        mLoading = rootView.findViewById(R.id.progressBar1)
        mControllers = rootView.findViewById(R.id.controllers)

        // Init waveform
        val displaySize = Point()
        activity!!.windowManager.defaultDisplay.getSize(displaySize)

        waveformView = rootView.findViewById(R.id.waveformView)
        waveformHandler = WaveformHandler(waveformView)
        waveformView.setDisplaySize(displaySize)
        waveformView.setCallback(object : WaveformView.Callback {
            override fun onSeek(position: Long) {
                seek(position)
            }

            override fun onSeeking() {
                stopWaveformUpdate()
            }

            override fun onCuePointSetText(mediaId: String, position: Int, text: String) {
                DatabaseHelper.instance.setDescription(mediaId, position, text)
            }

            override fun onCuePointDelete(mediaId: String, position: Int) {
                DatabaseHelper.instance.deleteCuePoint(mediaId, position)
            }

            override fun onWaveformLoaded() {
                scheduleWaveformUpdate()
                if ((activity as MusicPlayerActivity).slidingUpPanelLayout.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                    ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.WAVEFORM_SEEKING, activity as MusicPlayerActivity)
                    ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.CUE_POINT, activity as MusicPlayerActivity)
                }
            }
        })


        mSkipNext.setOnClickListener {
            val controls1 = MediaControllerCompat.getMediaController(activity!!).transportControls
            controls1.skipToNext()
        }

        mSkipPrev.setOnClickListener {
            val controls12 = MediaControllerCompat.getMediaController(activity!!).transportControls
            controls12.skipToPrevious()
        }

        mPlayPause.setOnClickListener {
            val state = MediaControllerCompat.getMediaController(activity!!).playbackState
            if (state != null) {
                val controls13 = MediaControllerCompat.getMediaController(activity!!).transportControls
                when (state.state) {
                    PlaybackStateCompat.STATE_PLAYING // fall through
                        , PlaybackStateCompat.STATE_BUFFERING -> {
                        controls13.pause()
                        stopSeekbarUpdate()
                    }
                    PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> {
                        controls13.play()
                        scheduleSeekbarUpdate()
                    }
                    else -> LogHelper.d(TAG, "onClick with state ", state.state)
                }
            }
        }

        rootView.findViewById<View>(R.id.star).setOnClickListener {
            if (mCurrentMetadata != null) {
                waveformHandler.addCuePoint(mCurrentMetadata!!, currentPosition, duration)
            }
        }

        val startShazam = rootView.findViewById<ImageView>(R.id.shazam)
        if (Utils.isAppInstalled(rootView.context, "com.shazam.android")) {
            startShazam.setOnClickListener {
                val intent = Intent("com.shazam.android.intent.actions.START_TAGGING")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        } else {
            startShazam.visibility = View.GONE
        }

        mSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                mStart.text = DateUtils.formatElapsedTime((progress / 1000).toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                stopSeekbarUpdate()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                seek(seekBar.progress.toLong())
                scheduleSeekbarUpdate()
            }
        })

        return rootView
    }

    internal fun onMediaControllerConnected() {
        val controller = MediaControllerCompat.getMediaController(activity!!)
        LogHelper.d(TAG, "onConnected, mediaController==null? ", controller == null)
        if (controller == null) {
            return
        }
        val state = controller.playbackState
        val metadata = controller.metadata
        onMetadataChanged(metadata)
        onPlaybackStateChanged(state)

        if (metadata != null && (mCurrentMetadata == null || metadata.description.mediaId != mCurrentMetadata!!.description.mediaId)) {
            mCurrentMetadata = metadata
            updateMetadata(metadata)
            updateMediaDescription(metadata.description)
        }
        updateProgress()
        if (state != null && (state.state == PlaybackStateCompat.STATE_PLAYING || state.state == PlaybackStateCompat.STATE_BUFFERING)) {
            scheduleSeekbarUpdate()
        }
    }

    fun onMetadataChanged(metadata: MediaMetadataCompat?) {
        if (metadata == null) {
            return
        }
        LogHelper.d(TAG, "Received metadata state change to mediaId=",
                metadata.description.mediaId,
                " song=", metadata.description.title)

        mTitle.text = metadata.description.title
        mSubtitle.text = metadata.description.subtitle
        updateMetadata(metadata)
        if (mCurrentMetadata == null || metadata.description.mediaId != mCurrentMetadata!!.description.mediaId) {
            mCurrentMetadata = metadata
            updateMediaDescription(metadata.description)
        }
    }

    fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
        LogHelper.d(TAG, "Received playback state change to state ", state!!.state)
        var enablePlay = false
        when (state.state) {
            PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> enablePlay = true
            PlaybackStateCompat.STATE_ERROR -> LogHelper.e(TAG, "error playbackstate: ", state.errorMessage)
        }

        if (enablePlay) {
            mPlayPauseSmall.setImageDrawable(
                    ContextCompat.getDrawable(activity!!, R.drawable.ic_play_arrow_black_36dp))
        } else {
            mPlayPauseSmall.setImageDrawable(
                    ContextCompat.getDrawable(activity!!, R.drawable.ic_pause_black_36dp))
        }
        updatePlaybackState(state)
    }

    private fun playMedia() {
        val controller = MediaControllerCompat.getMediaController(activity!!)
        controller?.transportControls?.play()
    }

    private fun pauseMedia() {
        val controller = MediaControllerCompat.getMediaController(activity!!)
        controller?.transportControls?.pause()
    }

    private fun seek(position: Long) {
        if (MediaControllerCompat.getMediaController(activity!!) != null) {
            MediaControllerCompat.getMediaController(activity!!).transportControls.seekTo(position)
        }
    }

    private fun scheduleSeekbarUpdate() {
        stopSeekbarUpdate()
        if (!mExecutorService.isShutdown) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate({ mHandler.post(mUpdateProgressTask) }, PROGRESS_UPDATE_INITIAL_INTERVAL,
                    PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS)
        }
        scheduleWaveformUpdate()
    }

    private fun scheduleWaveformUpdate() {
        stopWaveformUpdate()
        if (duration > 0 && waveformView.desiredWidth > 0 && !mExecutorService.isShutdown) {
            mScheduleFutureWaveform = mExecutorService.scheduleAtFixedRate({
                mHandler.post(mUpdateWaveformProgressTask) }, PROGRESS_UPDATE_INITIAL_INTERVAL,
                    Math.max(duration / waveformView.desiredWidth, 1).toLong(), TimeUnit.MILLISECONDS)
        }
    }

    private fun stopSeekbarUpdate() {
        if (mScheduleFuture != null) {
            mScheduleFuture!!.cancel(false)
        }
        stopWaveformUpdate()
    }

    private fun stopWaveformUpdate() {
        if (mScheduleFutureWaveform != null) {
            mScheduleFutureWaveform!!.cancel(false)
        }
    }

    private fun updateMetadata(metadata: MediaMetadataCompat) {
        LogHelper.d(TAG,"duration=", metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
        duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
        mSeekbar.max = duration
        mEnd.text = DateUtils.formatElapsedTime((duration / 1000).toLong())
    }

    private fun updateMediaDescription(description: MediaDescriptionCompat?) {
        if (description == null) {
            return
        }
        mLine1.text = description.title
        mLine2.text = description.subtitle

        fetchImageAsync(description)
    }

    private fun fetchImageAsync(description: MediaDescriptionCompat) {
        // get artwork from uri online or from cache
        ArtworkHelper.loadArtwork(requests!!, description, mBackgroundImage, object : ArtworkHelper.ColorsListener {
            override fun onColorsReady(colors: IntArray) {
                val vibrantColor = colors[0]
                val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), this@FullScreenPlayerFragment.vibrantColor, vibrantColor)
                colorAnimator.addUpdateListener { valueAnimator ->
                    val color = valueAnimator.animatedValue as Int
                    mSeekbar.progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        mSeekbar.thumb.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                    }
                    mPlayPause.setColorFilter(color)
                    mSkipNext.setColorFilter(color)
                    mSkipPrev.setColorFilter(color)
                    waveformView.colorizeWaveform(color)
                    val activity = activity as MusicPlayerActivity
                    activity.collapsingToolbarLayout.setBackgroundColor(color)
                    activity.mNavigationView.setBackgroundColor(color)
                    activity.controls.setBackgroundColor(color)
                    activity.slidingUpPanelLayout.setBackgroundColor(color)
                }
                colorAnimator.start()
                this@FullScreenPlayerFragment.vibrantColor = vibrantColor

                val textColor = colors[1]
                val textAnimator = ValueAnimator.ofObject(ArgbEvaluator(), this@FullScreenPlayerFragment.textColor, textColor)
                textAnimator.addUpdateListener { valueAnimator ->
                    val color = valueAnimator.animatedValue as Int
                    (activity as MusicPlayerActivity).mToolbar.setTitleTextColor(color)
                    mTitle.setTextColor(color)
                    mSubtitle.setTextColor(color)
                }
                textAnimator.start()
                this@FullScreenPlayerFragment.textColor = textColor
            }

            override fun onError() {
                mSeekbar.progressDrawable.setColorFilter(ContextCompat.getColor(activity!!, R.color.colorPrimary), PorterDuff.Mode.SRC_IN)
                mSeekbar.thumb.setColorFilter(ContextCompat.getColor(activity!!, R.color.colorPrimary), PorterDuff.Mode.SRC_IN)
                waveformView.colorizeWaveform(ContextCompat.getColor(activity!!, R.color.colorPrimary))
            }
        })

        // Playback Controls
        ArtworkHelper.loadArtwork(requests!!, description, mAlbumArt)

        // load waveform
        waveformHandler.loadWaveform(requests!!, mCurrentMetadata!!, duration)
        updateWaveformProgress()
    }

    private fun updatePlaybackState(state: PlaybackStateCompat?) {
        if (state == null) {
            return
        }
        LogHelper.d(TAG, "updatePlaybackState=", state.state)
        mLastPlaybackState = state
        mLine3.text = ""
        when (state.state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                mLoading.visibility = INVISIBLE
                mPlayPause.visibility = VISIBLE
                mPlayPause.setImageDrawable(mPauseDrawable)
                mControllers.visibility = VISIBLE
                scheduleSeekbarUpdate()
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                mControllers.visibility = VISIBLE
                mLoading.visibility = INVISIBLE
                mPlayPause.visibility = VISIBLE
                mPlayPause.setImageDrawable(mPlayDrawable)
                stopSeekbarUpdate()
            }
            PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_STOPPED -> {
                mLoading.visibility = INVISIBLE
                mPlayPause.visibility = VISIBLE
                mPlayPause.setImageDrawable(mPlayDrawable)
                stopSeekbarUpdate()
            }
            PlaybackStateCompat.STATE_BUFFERING -> {
                mPlayPause.visibility = INVISIBLE
                mLoading.visibility = VISIBLE
                mLine3.text = getString(R.string.loading)
                stopSeekbarUpdate()
            }
            else -> LogHelper.d(TAG, "Unhandled state ", state.state)
        }

        mSkipNext.visibility = if (state.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT == 0L)
            INVISIBLE
        else
            VISIBLE
        mSkipPrev.visibility = if (state.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS == 0L)
            INVISIBLE
        else
            VISIBLE
    }

    private fun updateProgress() {
        if (mLastPlaybackState == null) {
            return
        }
        currentPosition = mLastPlaybackState!!.position.toInt()
        if (mLastPlaybackState!!.state != PlaybackStateCompat.STATE_PAUSED) {
            // Calculate the elapsed time between the last position update and now and unless
            // paused, we can assume (delta * speed) + current position is approximately the
            // latest position. This ensure that we do not repeatedly call the getPlaybackState()
            // on MediaControllerCompat.
            val timeDelta = SystemClock.elapsedRealtime() - mLastPlaybackState!!.lastPositionUpdateTime
            currentPosition += Math.round(timeDelta * mLastPlaybackState!!.playbackSpeed)
        }
        mSeekbar.progress = currentPosition
    }

    private fun updateWaveformProgress() {
        if (mLastPlaybackState == null) {
            return
        }
        var currentPosition = mLastPlaybackState!!.position
        if (mLastPlaybackState!!.state != PlaybackStateCompat.STATE_PAUSED && mLastPlaybackState!!.state != PlaybackStateCompat.STATE_BUFFERING
                && mLastPlaybackState!!.state != PlaybackStateCompat.STATE_NONE) {
            // Calculate the elapsed time between the last position update and now and unless
            // paused, we can assume (delta * speed) + current position is approximately the
            // latest position. This ensure that we do not repeatedly call the getPlaybackState()
            // on MediaControllerCompat.
            val timeDelta = SystemClock.elapsedRealtime() - mLastPlaybackState!!.lastPositionUpdateTime
            currentPosition += (timeDelta.toInt() * mLastPlaybackState!!.playbackSpeed).toLong()
        }
        waveformView.setProgress(currentPosition.toInt(), duration)
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(FullScreenPlayerFragment::class.java)
        private const val PROGRESS_UPDATE_INTERNAL: Long = 1000
        private const val PROGRESS_UPDATE_INITIAL_INTERVAL: Long = 100
    }
}
