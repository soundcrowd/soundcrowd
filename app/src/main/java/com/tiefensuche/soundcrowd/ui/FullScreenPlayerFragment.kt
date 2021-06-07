/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.*
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.database.Database
import com.tiefensuche.soundcrowd.database.Database.Companion.POSITION
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.ui.BaseActivity.Companion.MIME_TEXT
import com.tiefensuche.soundcrowd.ui.intro.ShowcaseViewManager
import com.tiefensuche.soundcrowd.utils.Utils
import com.tiefensuche.soundcrowd.waveform.WaveformHandler
import com.tiefensuche.soundcrowd.waveform.WaveformView
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * A full screen player that shows the current playing music with a background image
 * depicting the album art. The activity also has controls to seek/pause/play the audio.
 */
internal class FullScreenPlayerFragment : Fragment() {

    private val mHandler = Handler()
    private val mExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var mLastPlaybackState: PlaybackStateCompat? = null
    private var currentPosition: Int = 0
    private lateinit var mControllers: View
    private lateinit var mMediaBrowserProvider: MediaBrowserProvider
    private var mCurrentMetadata: MediaMetadataCompat? = null
    private lateinit var waveformView: WaveformView
    private var mDuration: Int = 0
    private lateinit var requests: GlideRequests
    private lateinit var mTitle: TextView
    private lateinit var mSubtitle: TextView
    private lateinit var mDescription: TextView
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
    private lateinit var mShare: ImageView
    private lateinit var mLike: ImageView
    private lateinit var mBackgroundImage: ImageView
    private lateinit var mWaveformHandler: WaveformHandler

    // current colors, used as from values in the transition to the new color
    private var mVibrantColor: Int = 0
    private var mTextColor: Int = 0

    private var mScheduleFuture: ScheduledFuture<*>? = null
    private var mScheduleFutureWaveform: ScheduledFuture<*>? = null

    private val mButtonListener = OnClickListener(function = fun(view: View) {
        val activity = activity ?: return
        val controller = MediaControllerCompat.getMediaController(activity)
        val state = controller.playbackState?.state ?: PlaybackStateCompat.STATE_NONE
        Log.d(TAG, "Button pressed, in state $state")
        when (view.id) {
            R.id.play_pause -> {
                Log.d(TAG, "Play button pressed, in state $state")
                when (state) {
                    PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.STATE_STOPPED,
                    PlaybackStateCompat.STATE_NONE -> playMedia()
                    PlaybackStateCompat.STATE_PLAYING,
                    PlaybackStateCompat.STATE_BUFFERING,
                    PlaybackStateCompat.STATE_CONNECTING -> pauseMedia()
                }
            }
        }
    })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_full_player, container, false)
        mPlayPauseSmall = rootView.findViewById(R.id.play_pause)
        mPlayPauseSmall.isEnabled = true
        mPlayPauseSmall.setOnClickListener(mButtonListener)

        mTitle = rootView.findViewById(R.id.title)
        mSubtitle = rootView.findViewById(R.id.artist)
        mDescription = rootView.findViewById(R.id.description)
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
        mLoading = rootView.findViewById(R.id.progressBar)
        mControllers = rootView.findViewById(R.id.controllers)
        mLike = rootView.findViewById(R.id.favorite)

        // Init waveform
        val displaySize = Point()
        activity?.windowManager?.defaultDisplay?.getSize(displaySize)

        waveformView = rootView.findViewById(R.id.waveformView)
        mWaveformHandler = WaveformHandler(waveformView)
        waveformView.setDisplaySize(displaySize)
        waveformView.setCallback(object : WaveformView.Callback {
            override fun onSeek(position: Long) {
                seek(position)
            }

            override fun onSeeking() {
                stopWaveformUpdate()
            }

            override fun onCuePointSetText(mediaId: String, position: Int, text: String) {
                MusicService.database.setDescription(mediaId, position, text)
            }

            override fun onCuePointDelete(mediaId: String, position: Int) {
                MusicService.database.deleteCuePoint(mediaId, position)
            }

            override fun onWaveformLoaded() {
                val activity = activity
                if (activity is MusicPlayerActivity && activity.slidingUpPanelLayout.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                    ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.WAVEFORM_SEEKING, activity)
                    ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.CUE_POINT, activity)
                }
            }
        })

        mSkipNext.setOnClickListener {
            activity?.let {
                MediaControllerCompat.getMediaController(it).transportControls.skipToNext()
            }
        }

        mSkipPrev.setOnClickListener {
            activity?.let {
                MediaControllerCompat.getMediaController(it).transportControls.skipToPrevious()
            }
        }

        mPlayPause.setOnClickListener {
            activity?.let {
                val state = MediaControllerCompat.getMediaController(it).playbackState
                val controls = MediaControllerCompat.getMediaController(it).transportControls
                when (state.state) {
                    PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.STATE_BUFFERING -> {
                        controls.pause()
                        stopSeekbarUpdate()
                    }
                    PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> {
                        controls.play()
                        scheduleSeekbarUpdate()
                    }
                    else -> Log.d(TAG, "onClick with state ${state.state}")
                }
            }
        }

        mShare = rootView.findViewById(R.id.share)
        mShare.setOnClickListener {
            mCurrentMetadata?.let {
                val sharingIntent = Intent(Intent.ACTION_SEND)
                it.getString(MediaMetadataCompatExt.METADATA_KEY_URL)?.let {
                    sharingIntent.type = MIME_TEXT
                    sharingIntent.putExtra(Intent.EXTRA_TEXT, it)
                    startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_via)))
                }
            }
        }

        rootView.findViewById<View>(R.id.star).setOnClickListener {
            addCuePoint()
        }

        val startShazam = rootView.findViewById<ImageView>(R.id.shazam)
        if (Utils.isAppInstalled(rootView.context, "com.shazam.android")) {
            startShazam.setOnClickListener {
                val intent = Intent("com.shazam.android.intent.actions.START_TAGGING")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        } else {
            startShazam.visibility = GONE
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

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        if (activity is MediaBrowserProvider)
            mMediaBrowserProvider = activity
    }

    internal fun onMediaControllerConnected() {
        activity?.let {
            val controller = MediaControllerCompat.getMediaController(it) ?: return
            val state = controller.playbackState
            onPlaybackStateChanged(state)
            onMetadataChanged(controller.metadata)

            updateProgress()
            if (state != null && (state.state == PlaybackStateCompat.STATE_PLAYING || state.state == PlaybackStateCompat.STATE_BUFFERING)) {
                scheduleSeekbarUpdate()
            }
        }
    }

    internal fun onMetadataChanged(metadata: MediaMetadataCompat?) {

        if (metadata != null) {
            // Small sliding-up bar
            mLine1.text = metadata.description.title
            mLine2.text = metadata.description.subtitle

            // Main fragment
            mTitle.text = metadata.description.title
            mSubtitle.text = metadata.description.subtitle
            mDescription.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)
            mDuration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
            mSeekbar.max = mDuration
            mEnd.text = DateUtils.formatElapsedTime((mDuration / 1000).toLong())
            mWaveformHandler.loadWaveform(requests, metadata, mDuration)

            mShare.visibility = if (metadata.containsKey(MediaMetadataCompatExt.METADATA_KEY_URL)) VISIBLE else GONE

            metadata.getRating(MediaMetadataCompatExt.METADATA_KEY_FAVORITE)?.let { rating ->
                mLike.setColorFilter(if (rating.hasHeart()) Color.RED else Color.WHITE)
                mLike.setOnClickListener {
                    activity?.let { it1 -> MediaControllerCompat.getMediaController(it1).transportControls.setRating(RatingCompat.newHeartRating(!rating.hasHeart())) }
                }
                mLike.visibility = VISIBLE
            } ?: run {
                mLike.visibility = GONE
            }

            mCurrentMetadata = metadata
            fetchImageAsync(metadata.description)
        }
    }

    internal fun onPlaybackStateChanged(state: PlaybackStateCompat?) {

        Log.d(TAG, "Received playback state change to state ${state?.state}")
        var enablePlay = false
        when (state?.state) {
            PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> enablePlay = true
            PlaybackStateCompat.STATE_ERROR -> Log.e(TAG, "error playbackstate: ${state.errorMessage}")
        }

        context?.let {
            if (enablePlay) {
                mPlayPauseSmall.setImageDrawable(
                        ContextCompat.getDrawable(it, R.drawable.ic_play_arrow_black_36dp))
            } else {
                mPlayPauseSmall.setImageDrawable(
                        ContextCompat.getDrawable(it, R.drawable.ic_pause_black_36dp))
            }
        }

        updatePlaybackState(state)
    }

    private fun playMedia() {
        activity?.let {
            MediaControllerCompat.getMediaController(it).transportControls?.play()
        }
    }

    private fun pauseMedia() {
        activity?.let {
            MediaControllerCompat.getMediaController(it).transportControls?.pause()
        }
    }

    private fun seek(position: Long) {
        activity?.let {
            if (MediaControllerCompat.getMediaController(it) != null) {
                MediaControllerCompat.getMediaController(it).transportControls.seekTo(position)
            }
        }
    }

    private fun scheduleSeekbarUpdate() {
        stopSeekbarUpdate()
        if (!mExecutorService.isShutdown) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate({ mHandler.post(mUpdateProgressTask) }, PROGRESS_UPDATE_INITIAL_INTERVAL,
                    PROGRESS_UPDATE_INTERNAL.coerceAtMost((mDuration / waveformView.desiredWidth).toLong().coerceAtLeast(1)), TimeUnit.MILLISECONDS)
        }
    }

    private fun stopSeekbarUpdate() {
        mScheduleFuture?.cancel(false)
        stopWaveformUpdate()
    }

    private fun stopWaveformUpdate() {
        mScheduleFutureWaveform?.cancel(false)
    }

    private fun fetchImageAsync(description: MediaDescriptionCompat) {
        Log.d(TAG, "fetchImageAsync, ${description.mediaId}")
        // get artwork from uri online or from cache
        ArtworkHelper.loadArtwork(requests, description, mBackgroundImage, object : ArtworkHelper.ColorsListener {
            override fun onColorsReady(colors: IntArray) {
                val vibrantColor = colors[0]
                val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), mVibrantColor, vibrantColor)
                colorAnimator.addUpdateListener { valueAnimator ->
                    val color = valueAnimator.animatedValue
                    if (color is Int) {
                        mSeekbar.progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            mSeekbar.thumb.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                        }

                        for (view in listOf(mPlayPause, mSkipNext, mSkipPrev)) {
                            view.setColorFilter(color)
                        }

                        waveformView.colorizeWaveform(color)

                        val activity = activity
                        if (activity is MusicPlayerActivity) {
                            for (view in listOf(activity.collapsingToolbarLayout, activity.mNavigationView, activity.controls, activity.slidingUpPanelLayout)) {
                                view.setBackgroundColor(color)
                            }
                        }
                    }
                }
                colorAnimator.start()
                mVibrantColor = vibrantColor

                val textColor = colors[1]
                val textAnimator = ValueAnimator.ofObject(ArgbEvaluator(), mTextColor, textColor)
                textAnimator.addUpdateListener { valueAnimator ->
                    val color = valueAnimator.animatedValue
                    if (color is Int) {
                        mTitle.setTextColor(color)
                        mSubtitle.setTextColor(color)
                        val activity = activity
                        if (activity is MusicPlayerActivity) {
                            activity.mToolbar.setTitleTextColor(color)
                        }
                    }
                }
                textAnimator.start()
                mTextColor = textColor
            }

            override fun onError() {
                activity?.let {
                    for (view in listOf(mSeekbar.progressDrawable, mSeekbar.thumb)) {
                        view.setColorFilter(ContextCompat.getColor(it, R.color.colorPrimary), PorterDuff.Mode.SRC_IN)
                    }
                    waveformView.colorizeWaveform(ContextCompat.getColor(it, R.color.colorPrimary))
                }
            }
        })

        // Playback Controls
        ArtworkHelper.loadArtwork(requests, description, mAlbumArt)
    }

    private fun updatePlaybackState(state: PlaybackStateCompat?) {
        if (state == null) {
            return
        }
        Log.d(TAG, "updatePlaybackState=${state.state}")
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
            else -> Log.d(TAG, "Unhandled state ${state.state}")
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
        mLastPlaybackState?.let {
            currentPosition = it.position.toInt()
            if (it.state != PlaybackStateCompat.STATE_PAUSED) {
                // Calculate the elapsed time between the last position update and now and unless
                // paused, we can assume (delta * speed) + current position is approximately the
                // latest position. This ensure that we do not repeatedly call the getPlaybackState()
                // on MediaControllerCompat.
                val timeDelta = SystemClock.elapsedRealtime() - it.lastPositionUpdateTime
                currentPosition += (timeDelta * it.playbackSpeed).roundToInt()
            }
            mSeekbar.progress = currentPosition
            waveformView.setProgress(currentPosition, mDuration)
        }
    }

    internal fun addCuePoint(text: String? = null) {
        mCurrentMetadata?.let {
            val bundle = Bundle()
            bundle.putInt(POSITION, currentPosition)
            mMediaBrowserProvider.mediaBrowser?.sendCustomAction(MusicProvider.ACTION_ADD_CUE_POINT, bundle, null)
            mWaveformHandler.addCuePoint(it, currentPosition, mDuration, text ?: "")
        }
    }

    companion object {
        private val TAG = FullScreenPlayerFragment::class.simpleName
        private const val PROGRESS_UPDATE_INTERNAL: Long = 1000
        private const val PROGRESS_UPDATE_INITIAL_INTERVAL: Long = 100
    }
}