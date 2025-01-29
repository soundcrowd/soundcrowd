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
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT
import android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
import android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_NONE
import android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ONE
import android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_ALL
import android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_NONE
import android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING
import android.support.v4.media.session.PlaybackStateCompat.STATE_CONNECTING
import android.support.v4.media.session.PlaybackStateCompat.STATE_ERROR
import android.support.v4.media.session.PlaybackStateCompat.STATE_NONE
import android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
import android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
import android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.playback.PlaybackManager.Companion.CUSTOM_ACTION_ADD_CUE_POINT
import com.tiefensuche.soundcrowd.playback.PlaybackManager.Companion.CUSTOM_ACTION_REMOVE_CUE_POINT
import com.tiefensuche.soundcrowd.playback.PlaybackManager.Companion.CUSTOM_ACTION_SET_CUE_POINT
import com.tiefensuche.soundcrowd.service.Share
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Cues.DESCRIPTION
import com.tiefensuche.soundcrowd.sources.MusicProvider.Cues.POSITION
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

    private fun activity() : MusicPlayerActivity {
        return super.getActivity() as MusicPlayerActivity
    }

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
    private lateinit var mRepeat: ImageView
    private lateinit var mShuffle: ImageView
    private lateinit var mBackgroundImage: ImageView
    private lateinit var mWaveformHandler: WaveformHandler

    // current colors, used as from values in the transition to the new color
    private var mVibrantColor: Int = 0
    private var mTextColor: Int = 0

    private var mScheduleFuture: ScheduledFuture<*>? = null

    private val mButtonListener = OnClickListener(function = fun(view: View) {
        val activity = activity ?: return
        val controller = MediaControllerCompat.getMediaController(activity)
        val state = controller.playbackState?.state ?: STATE_NONE
        Log.d(TAG, "Button pressed, in state $state")
        when (view.id) {
            R.id.play_pause -> {
                Log.d(TAG, "Play button pressed, in state $state")
                when (state) {
                    STATE_PAUSED, STATE_STOPPED, STATE_NONE -> playMedia()
                    STATE_PLAYING, STATE_BUFFERING, STATE_CONNECTING -> pauseMedia()
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
        mPauseDrawable = ContextCompat.getDrawable(rootView.context, R.drawable.ic_round_pause_24)
        mPlayDrawable = ContextCompat.getDrawable(rootView.context, R.drawable.ic_round_play_arrow_24)
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
        mRepeat = rootView.findViewById(R.id.repeat)
        mShuffle = rootView.findViewById(R.id.shuffle)

        // Init waveform
        val displaySize = Point()
        activity?.windowManager?.defaultDisplay?.getSize(displaySize)

        waveformView = rootView.findViewById(R.id.waveformView)
        mWaveformHandler = WaveformHandler(waveformView)
        waveformView.setDisplaySize(displaySize)
        waveformView.setCallback(object : WaveformView.Callback {
            override fun onSeek(position: Long) {
                seek(position)
                activity().slidingUpPanelLayout.isTouchEnabled = true
            }

            override fun onSeeking() {
                activity().slidingUpPanelLayout.isTouchEnabled = false
            }

            override fun onCuePointSetText(mediaId: String, position: Int, text: String) {
                setCuePoint(position, text)
            }

            override fun onCuePointDelete(mediaId: String, position: Int) {
                deleteCuePoint(position)
            }

            override fun onWaveformLoaded() {
                if (activity().slidingUpPanelLayout.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                    ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.WAVEFORM_SEEKING, activity())
                    ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.CUE_POINT, activity())
                }
            }
        })

        mSkipNext.setOnClickListener {
            MediaControllerCompat.getMediaController(activity()).transportControls.skipToNext()
        }

        mSkipPrev.setOnClickListener {
            MediaControllerCompat.getMediaController(activity()).transportControls.skipToPrevious()
        }

        mPlayPause.setOnClickListener {
            val state = MediaControllerCompat.getMediaController(activity()).playbackState
            val controls = MediaControllerCompat.getMediaController(activity()).transportControls
            when (state.state) {
                STATE_PLAYING, STATE_BUFFERING -> {
                    controls.pause()
                    stopSeekbarUpdate()
                }
                STATE_NONE, STATE_PAUSED, STATE_STOPPED -> {
                    controls.play()
                    scheduleSeekbarUpdate()
                }
                else -> Log.d(TAG, "onClick with state ${state.state}")
            }
        }

        mShare = rootView.findViewById(R.id.share)
        mShare.setOnClickListener {
            mCurrentMetadata?.let { it ->
                it.getString(MediaMetadataCompatExt.METADATA_KEY_URL)?.let {
                    Share.shareText(rootView.context, it)
                }
            }
        }

        mRepeat.setOnClickListener {
            activity?.let {
                val controller = MediaControllerCompat.getMediaController(it)
                when (controller.repeatMode) {
                    REPEAT_MODE_NONE -> controller.transportControls.setRepeatMode(REPEAT_MODE_ONE)
                    REPEAT_MODE_ONE -> controller.transportControls.setRepeatMode(REPEAT_MODE_NONE)
                }
            }
        }

        mShuffle.setOnClickListener {
            activity?.let {
                val controller = MediaControllerCompat.getMediaController(it)
                when (controller.shuffleMode) {
                    SHUFFLE_MODE_NONE -> controller.transportControls.setShuffleMode(SHUFFLE_MODE_ALL)
                    SHUFFLE_MODE_ALL -> controller.transportControls.setShuffleMode(SHUFFLE_MODE_NONE)
                }
            }
        }

        rootView.findViewById<View>(R.id.star).setOnClickListener {
            addCuePoint()
        }

        rootView.findViewById<View>(R.id.replay).setOnClickListener {
            seek((currentPosition - 10000).toLong())
        }

        rootView.findViewById<View>(R.id.forward).setOnClickListener {
            seek((currentPosition + 10000).toLong())
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

    override fun onStop() {
        super.onStop()
        stopSeekbarUpdate()
    }

    internal fun onMediaControllerConnected() {
        val controller = MediaControllerCompat.getMediaController(activity()) ?: return
        val state = controller.playbackState
        onPlaybackStateChanged(state)
        onMetadataChanged(controller.metadata)
        updateRepeatMode(controller.repeatMode)
        updateShuffleMode(controller.shuffleMode)
        updateProgress()
        if (state != null && (state.state == STATE_PLAYING || state.state == STATE_BUFFERING)) {
            scheduleSeekbarUpdate()
        }
    }

    internal fun onMetadataChanged(metadata: MediaMetadataCompat?) {
        if (metadata == null)
            return

        if (metadata.description.mediaId != mCurrentMetadata?.description?.mediaId) {
            // Small sliding-up bar
            mLine1.text = metadata.description.title
            mLine2.text = metadata.description.subtitle

            // Main fragment
            mTitle.text = metadata.description.title
            mSubtitle.text = metadata.description.subtitle
            mDescription.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)
            mDuration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
            mSeekbar.max = mDuration
            mSeekbar.progress = 0
            mEnd.text = DateUtils.formatElapsedTime((mDuration / 1000).toLong())
            mWaveformHandler.loadWaveform(requests, metadata, mDuration)
            waveformView.setProgress(0)

            mShare.visibility = if (metadata.containsKey(MediaMetadataCompatExt.METADATA_KEY_URL)) VISIBLE else GONE

            fetchImageAsync(metadata.description)
            mCurrentMetadata = metadata
        }

        metadata.getRating(MediaMetadataCompatExt.METADATA_KEY_FAVORITE)?.let { rating ->
            mLike.setColorFilter(if (rating.hasHeart()) Color.RED else Color.WHITE)
            mLike.setOnClickListener {
                activity?.let { MediaControllerCompat.getMediaController(it).transportControls.setRating(RatingCompat.newHeartRating(!rating.hasHeart())) }
            }
            mLike.visibility = VISIBLE
        } ?: run {
            mLike.visibility = GONE
        }
    }

    internal fun onPlaybackStateChanged(state: PlaybackStateCompat?) {

        Log.d(TAG, "Received playback state change to state ${state?.state}")
        var enablePlay = false
        when (state?.state) {
            STATE_NONE, STATE_PAUSED, STATE_STOPPED -> enablePlay = true
            STATE_ERROR -> Log.e(TAG, "error playbackstate: ${state.errorMessage}")
        }

        context?.let {
            if (enablePlay) {
                mPlayPauseSmall.setImageDrawable(
                        ContextCompat.getDrawable(it, R.drawable.ic_round_play_arrow_24))
            } else {
                mPlayPauseSmall.setImageDrawable(
                        ContextCompat.getDrawable(it, R.drawable.ic_round_pause_24))
            }
        }

        updatePlaybackState(state)
    }

    internal fun updateRepeatMode(repeatMode: Int) {
        mRepeat.setColorFilter(if (repeatMode == REPEAT_MODE_ONE) Color.RED else Color.WHITE)
    }

    internal fun updateShuffleMode(shuffleMode: Int) {
        mShuffle.setColorFilter(if (shuffleMode == SHUFFLE_MODE_ALL) Color.RED else Color.WHITE)
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
    }

    private fun fetchImageAsync(description: MediaDescriptionCompat) {
        Log.d(TAG, "fetchImageAsync, ${description.mediaId}")
        // get artwork from uri online or from cache
        ArtworkHelper.loadArtwork(requests, description, mBackgroundImage, object : ArtworkHelper.ColorsListener {
            override fun onColorsReady(colors: IntArray) {
                val vibrantColor = colors[0]
                if (mVibrantColor != vibrantColor) {
                    val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), mVibrantColor, vibrantColor)
                    colorAnimator.addUpdateListener { valueAnimator ->
                        val color = valueAnimator.animatedValue
                        if (color is Int) {
                            mSeekbar.progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                            mSeekbar.thumb.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                            for (view in listOf(mPlayPause, mSkipNext, mSkipPrev)) {
                                view.setColorFilter(color)
                            }
                            waveformView.colorizeWaveform(color)
                        }
                    }
                    colorAnimator.start()
                    mVibrantColor = vibrantColor
                }
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
            STATE_PLAYING -> {
                mLoading.visibility = INVISIBLE
                mPlayPause.visibility = VISIBLE
                mPlayPause.setImageDrawable(mPauseDrawable)
                mControllers.visibility = VISIBLE
                mSeekbar.isEnabled = true
                scheduleSeekbarUpdate()
            }
            STATE_PAUSED -> {
                mControllers.visibility = VISIBLE
                mLoading.visibility = INVISIBLE
                mPlayPause.visibility = VISIBLE
                mPlayPause.setImageDrawable(mPlayDrawable)
                stopSeekbarUpdate()
            }
            STATE_NONE, STATE_STOPPED -> {
                mLoading.visibility = INVISIBLE
                mPlayPause.visibility = VISIBLE
                mPlayPause.setImageDrawable(mPlayDrawable)
                stopSeekbarUpdate()
            }
            STATE_BUFFERING -> {
                mPlayPause.visibility = INVISIBLE
                mLoading.visibility = VISIBLE
                mLine3.text = getString(R.string.loading)
                mSeekbar.isEnabled = false
                stopSeekbarUpdate()
            }
            else -> Log.d(TAG, "Unhandled state ${state.state}")
        }

        mSkipNext.visibility = if (state.actions and ACTION_SKIP_TO_NEXT == 0L)
            INVISIBLE
        else
            VISIBLE
        mSkipPrev.visibility = if (state.actions and ACTION_SKIP_TO_PREVIOUS == 0L)
            INVISIBLE
        else
            VISIBLE
    }

    private fun updateProgress() {
        mLastPlaybackState?.let {
            currentPosition = it.position.toInt()
            if (it.state != STATE_PAUSED) {
                // Calculate the elapsed time between the last position update and now and unless
                // paused, we can assume (delta * speed) + current position is approximately the
                // latest position. This ensure that we do not repeatedly call the getPlaybackState()
                // on MediaControllerCompat.
                val timeDelta = SystemClock.elapsedRealtime() - it.lastPositionUpdateTime
                currentPosition += (timeDelta * it.playbackSpeed).roundToInt()
            }
            mSeekbar.progress = currentPosition
            waveformView.setProgress(currentPosition)
        }
    }

    private fun actionCuePoint(action: String, bundle: Bundle) {
        mCurrentMetadata?.let {
            MediaControllerCompat.getMediaController(activity()).transportControls.sendCustomAction(
                action,
                bundle
            )
        }
    }

    internal fun addCuePoint(text: String? = null) {
        val bundle = Bundle()
        bundle.putInt(POSITION, currentPosition)
        bundle.putString(DESCRIPTION, text)
        actionCuePoint(CUSTOM_ACTION_ADD_CUE_POINT, bundle)
        mCurrentMetadata?.let {
            mWaveformHandler.addCuePoint(
                it,
                currentPosition,
                mDuration,
                text ?: ""
            )
        }
    }

    private fun setCuePoint(position: Int, text: String) {
        val bundle = Bundle()
        bundle.putString(MEDIA_ID, mCurrentMetadata?.description?.mediaId)
        bundle.putInt(POSITION, position)
        bundle.putString(DESCRIPTION, text)
        actionCuePoint(CUSTOM_ACTION_SET_CUE_POINT, bundle)
    }

    private fun deleteCuePoint(position: Int) {
        val bundle = Bundle()
        bundle.putString(MEDIA_ID, mCurrentMetadata?.description?.mediaId)
        bundle.putInt(POSITION, position)
        actionCuePoint(CUSTOM_ACTION_REMOVE_CUE_POINT, bundle)
    }

    companion object {
        private val TAG = FullScreenPlayerFragment::class.simpleName
        private const val PROGRESS_UPDATE_INTERNAL: Long = 1000
        private const val PROGRESS_UPDATE_INITIAL_INTERVAL: Long = 100
    }
}