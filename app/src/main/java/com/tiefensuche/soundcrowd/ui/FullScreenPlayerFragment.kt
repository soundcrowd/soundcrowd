/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt.COMMAND_LIKE
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_CUE_CREATE
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_CUE_DELETE
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_CUE_EDIT
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_START_TAGGING
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.RESULT
import com.tiefensuche.soundcrowd.service.Share
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Cues.DESCRIPTION
import com.tiefensuche.soundcrowd.sources.MusicProvider.Cues.POSITION
import com.tiefensuche.soundcrowd.waveform.WaveformHandler
import com.tiefensuche.soundcrowd.waveform.WaveformView
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


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

    private lateinit var mControllers: View
    private lateinit var mMediaBrowserProvider: MediaBrowserProvider
    private var mCurrentMediaId: String? = null
    private lateinit var waveformView: WaveformView
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

    private var mScheduleFuture: ScheduledFuture<*>? = null

    private val mediaController: MediaController
            get() = activity().mediaBrowser

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_full_player, container, false)
        mPlayPauseSmall = rootView.findViewById(R.id.play_pause)
        mPlayPauseSmall.isEnabled = true
        mPlayPauseSmall.setOnClickListener {
            if (mediaController.isPlaying) {
                mediaController.pause()
                stopSeekbarUpdate()
            } else {
                mediaController.play()
                scheduleSeekbarUpdate()
            }
        }

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
                mediaController.seekTo(position)
                activity().sheetBehavior.isDraggable = true
            }

            override fun onSeeking() {
                activity().sheetBehavior.isDraggable = false
            }

            override fun onCuePointSetText(mediaId: String, position: Int, text: String) {
                setCuePoint(position, text)
            }

            override fun onCuePointDelete(mediaId: String, position: Int) {
                deleteCuePoint(position)
            }

            override fun onWaveformLoaded() {}
        })

        mSkipNext.setOnClickListener {
            mediaController.seekToNextMediaItem()
        }

        mSkipPrev.setOnClickListener {
            mediaController.seekToPreviousMediaItem()
        }

        mPlayPause.setOnClickListener {
            if (mediaController.isPlaying) {
                mediaController.pause()
                stopSeekbarUpdate()
            } else {
                mediaController.play()
                scheduleSeekbarUpdate()
            }
        }

        mShare = rootView.findViewById(R.id.share)
        mShare.setOnClickListener {
            mediaController.currentMediaItem?.mediaMetadata?.extras?.getString(
                MediaMetadataCompatExt.METADATA_KEY_URL)?.let {
                Share.shareText(rootView.context, it)
            }
        }

        mRepeat.setOnClickListener {
            when (mediaController.repeatMode) {
                Player.REPEAT_MODE_OFF -> {
                    mediaController.repeatMode = Player.REPEAT_MODE_ONE
                    mRepeat.setImageResource(androidx.media3.session.R.drawable.media3_icon_repeat_one)
                }
                Player.REPEAT_MODE_ONE -> {
                    mediaController.repeatMode = Player.REPEAT_MODE_ALL
                    mRepeat.setImageResource(androidx.media3.session.R.drawable.media3_icon_repeat_all)
                }
                Player.REPEAT_MODE_ALL -> {
                    mediaController.repeatMode = Player.REPEAT_MODE_OFF
                    mRepeat.setImageResource(androidx.media3.session.R.drawable.media3_icon_repeat_off)
                }
            }
        }

        mShuffle.setOnClickListener {
            mediaController.shuffleModeEnabled = !mediaController.shuffleModeEnabled
            if (mediaController.shuffleModeEnabled) {
                mShuffle.setImageResource(androidx.media3.session.R.drawable.media3_icon_shuffle_on)
            } else {
                mShuffle.setImageResource(androidx.media3.session.R.drawable.media3_icon_shuffle_off)
            }
        }

        rootView.findViewById<View>(R.id.star).setOnClickListener {
            addCuePoint()
        }

        rootView.findViewById<View>(R.id.replay).setOnClickListener {
            activity().mediaBrowser.seekTo(activity().mediaBrowser.currentPosition - 10000)
        }

        rootView.findViewById<View>(R.id.forward).setOnClickListener {
            activity().mediaBrowser.seekTo(activity().mediaBrowser.currentPosition + 10000)
        }

        val startTagging = rootView.findViewById<ImageView>(R.id.shazam)
        val pulse: Animation = AnimationUtils.loadAnimation(context, R.anim.pulse)
        startTagging.setOnClickListener {
            startTagging.isEnabled = false
            startTagging.setColorFilter(Color.RED)
            startTagging.startAnimation(pulse)
            mediaController.sendCustomCommand(SessionCommand(COMMAND_START_TAGGING, Bundle.EMPTY), Bundle()).also {
                it.addListener({
                    it.get().extras.let {
                        it.getString(RESULT)?.let { result ->
                            mWaveformHandler.addCuePoint(it.getInt(POSITION), mediaController.duration.toInt(), result)
                        } ?: Toast.makeText(context, "No result", Toast.LENGTH_SHORT).show()
                    }
                    startTagging.isEnabled = true
                    startTagging.setColorFilter(Color.WHITE)
                    startTagging.clearAnimation()
                }, ContextCompat.getMainExecutor(requireContext()))
            }
        }

        mSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                mStart.text = DateUtils.formatElapsedTime((progress / 1000).toLong())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                stopSeekbarUpdate()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                activity().mediaBrowser.seekTo(seekBar.progress.toLong())
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

    internal fun onMetadataChanged(metadata: MediaMetadata) {
        if (mediaController.currentMediaItem?.mediaId != mCurrentMediaId) {
            // Small sliding-up bar
            mLine1.text = metadata.title
            mLine2.text = metadata.artist

            // Main fragment
            mTitle.text = metadata.title
            mSubtitle.text = metadata.artist
            mDescription.text = metadata.description
            mEnd.text = DateUtils.formatElapsedTime(metadata.durationMs!! / 1000)
            mSeekbar.max = metadata.durationMs!!.toInt()
            mSeekbar.progress = 0
            mWaveformHandler.loadWaveform(requests, metadata, metadata.durationMs!!.toInt())

            mShare.visibility = if (metadata.extras?.containsKey(MediaMetadataCompatExt.METADATA_KEY_URL) == true) VISIBLE else GONE

            mCurrentMediaId = mediaController.currentMediaItem?.mediaId
            fetchImageAsync(mediaController.currentMediaItem!!)
        }

        (metadata.userRating as? HeartRating)?.let { rating ->
            mLike.setColorFilter(if (rating.isHeart) Color.RED else Color.WHITE)
            if (metadata.supportedCommands.contains(COMMAND_LIKE))
                mLike.setOnClickListener {
                    mediaController.sendCustomCommand(SessionCommand(COMMAND_LIKE, Bundle.EMPTY), Bundle())
                }
            mLike.visibility = VISIBLE
        } ?: run {
            mLike.visibility = GONE
        }
    }

    internal fun onPlaybackStateChanged() {
        if (mediaController.isPlaying) {
            mPlayPauseSmall.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_round_pause_24))
        } else {
            mPlayPauseSmall.setImageDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_round_play_arrow_24))
        }
        updatePlaybackState()
    }

    private fun scheduleSeekbarUpdate() {
        stopSeekbarUpdate()
        if (!mExecutorService.isShutdown) {
            mScheduleFuture = mExecutorService.scheduleWithFixedDelay({ mHandler.post(mUpdateProgressTask) }, PROGRESS_UPDATE_INITIAL_INTERVAL,
                    PROGRESS_UPDATE_INTERNAL.coerceAtMost((mediaController.duration / waveformView.desiredWidth).coerceAtLeast(1)), TimeUnit.MILLISECONDS)
        }
    }

    private fun stopSeekbarUpdate() {
        mScheduleFuture?.cancel(false)
    }

    private fun fetchImageAsync(item: MediaItem) {
        Log.d(TAG, "fetchImageAsync, ${item.mediaId}")
        // get artwork from uri online or from cache
        ArtworkHelper.loadArtwork(requests, item, mBackgroundImage, object : ArtworkHelper.ColorsListener {
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
        ArtworkHelper.loadArtwork(requests, item, mAlbumArt)
    }

    private fun updatePlaybackState() {
        mLine3.text = ""
        if (mediaController.playbackState == Player.STATE_BUFFERING) {
            mPlayPause.visibility = INVISIBLE
            mLoading.visibility = VISIBLE
            mLine3.text = getString(R.string.loading)
            mSeekbar.isEnabled = false
            stopSeekbarUpdate()
        } else if (mediaController.isPlaying) {
            mLoading.visibility = INVISIBLE
            mPlayPause.visibility = VISIBLE
            mPlayPause.setImageDrawable(mPauseDrawable)
            mSeekbar.isEnabled = true
            scheduleSeekbarUpdate()
        } else {
            mLoading.visibility = INVISIBLE
            mPlayPause.visibility = VISIBLE
            mPlayPause.setImageDrawable(mPlayDrawable)
            stopSeekbarUpdate()
        }

        mSkipNext.visibility = if (mediaController.hasNextMediaItem()) VISIBLE else INVISIBLE
        mSkipPrev.visibility = if (mediaController.hasPreviousMediaItem()) VISIBLE else INVISIBLE
    }

    private fun updateProgress() {
        mSeekbar.progress = mediaController.currentPosition.toInt()
        waveformView.setProgress(mediaController.currentPosition.toInt())
    }

    private fun actionCuePoint(action: String, bundle: Bundle) {
        mediaController.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), bundle)
    }

    private fun addCuePoint(text: String? = null) {
        val bundle = Bundle()
        bundle.putInt(POSITION, mediaController.currentPosition.toInt())
        bundle.putString(DESCRIPTION, text)
        actionCuePoint(COMMAND_CUE_CREATE, bundle)
        mWaveformHandler.addCuePoint(
            mediaController.currentPosition.toInt(),
            mediaController.duration.toInt(),
            text ?: ""
        )
    }

    private fun setCuePoint(position: Int, text: String) {
        val bundle = Bundle()
        bundle.putString(MEDIA_ID, mediaController.currentMediaItem!!.mediaId)
        bundle.putInt(POSITION, position)
        bundle.putString(DESCRIPTION, text)
        actionCuePoint(COMMAND_CUE_EDIT, bundle)
    }

    private fun deleteCuePoint(position: Int) {
        val bundle = Bundle()
        bundle.putString(MEDIA_ID, mediaController.currentMediaItem!!.mediaId)
        bundle.putInt(POSITION, position)
        actionCuePoint(COMMAND_CUE_DELETE, bundle)
    }

    companion object {
        private val TAG = FullScreenPlayerFragment::class.simpleName
        private const val PROGRESS_UPDATE_INTERNAL: Long = 1000
        private const val PROGRESS_UPDATE_INITIAL_INTERVAL: Long = 100
    }
}