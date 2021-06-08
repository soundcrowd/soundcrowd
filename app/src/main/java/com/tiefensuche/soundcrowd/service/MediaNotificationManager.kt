/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Looper
import android.os.RemoteException
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.MusicPlayerActivity
import com.tiefensuche.soundcrowd.utils.Utils

/**
 * Keeps track of a notification and updates it automatically for a given
 * MediaSession. Maintaining a visible notification (usually) guarantees that the music service
 * won't be killed during playback.
 */
internal class MediaNotificationManager @Throws(RemoteException::class)
constructor(private val mService: MusicService) : BroadcastReceiver() {

    private lateinit var mNotificationManager: NotificationManager
    private val mPauseIntent: PendingIntent
    private val mPlayIntent: PendingIntent
    private val mPreviousIntent: PendingIntent
    private val mNextIntent: PendingIntent
    private val mStopIntent: PendingIntent
    private val mNotificationColor: Int

    private var mSessionToken: MediaSessionCompat.Token? = null

    private var mController: MediaControllerCompat? = null
    private lateinit var mTransportControls: MediaControllerCompat.TransportControls
    private var mPlaybackState: PlaybackStateCompat? = null
    private var mMetadata: MediaMetadataCompat? = null
    private var mStarted = false

    private val mCb = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            mPlaybackState = state
            state?.let {
                if (state.state == PlaybackStateCompat.STATE_STOPPED || state.state == PlaybackStateCompat.STATE_NONE) {
                    stopNotification()
                } else {
                    val notification = createNotification()
                    if (state.state >= PlaybackStateCompat.STATE_PLAYING) {
                        mService.startForeground(NOTIFICATION_ID, notification)
                    } else {
                        mService.stopForeground(false)
                        mNotificationManager.notify(NOTIFICATION_ID, notification)
                    }
                }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            mMetadata = metadata
            val notification = createNotification()
            if (notification != null) {
                mNotificationManager.notify(NOTIFICATION_ID, notification)
            }
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            try {
                updateSessionToken()
            } catch (e: RemoteException) {
                Log.e(TAG, "could not connect media controller", e)
            }
        }
    }

    init {
        updateSessionToken()

        mNotificationColor = Utils.getThemeColor(mService, R.attr.colorPrimary,
                Color.DKGRAY)

        mNotificationManager = mService.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: throw IllegalStateException("can not get notification service")

        val pkg = mService.packageName
        mPauseIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                Intent(ACTION_PAUSE).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
        mPlayIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                Intent(ACTION_PLAY).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
        mPreviousIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                Intent(ACTION_PREV).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
        mNextIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                Intent(ACTION_NEXT).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)
        mStopIntent = PendingIntent.getBroadcast(mService, REQUEST_CODE,
                Intent(ACTION_STOP).setPackage(pkg), PendingIntent.FLAG_CANCEL_CURRENT)

        // Cancel all notifications to handle the case where the Service was killed and
        // restarted by the system.
        mNotificationManager.cancelAll()
    }

    /**
     * Posts the notification and starts tracking the session to keep it
     * updated. The notification will automatically be removed if the session is
     * destroyed before [.stopNotification] is called.
     */
    internal fun startNotification() {
        if (!mStarted) {
            mMetadata = mController?.metadata
            mPlaybackState = mController?.playbackState

            // The notification must be updated after setting started to true
            val notification = createNotification()
            if (notification != null && Looper.myLooper() != null) {
                mController?.registerCallback(mCb)
                val filter = IntentFilter()
                for (action in listOf(ACTION_NEXT, ACTION_PAUSE, ACTION_PLAY, ACTION_PREV)) {
                    filter.addAction(action)
                }
                mService.registerReceiver(this, filter)
                mService.startForeground(NOTIFICATION_ID, notification)
                mStarted = true
            }
        }
    }

    /**
     * Removes the notification and stops tracking the session. If the session
     * was destroyed this has no effect.
     */
    internal fun stopNotification() {
        if (mStarted) {
            mStarted = false
            mController?.unregisterCallback(mCb)
            try {
                mNotificationManager.cancel(NOTIFICATION_ID)
                mService.unregisterReceiver(this)
            } catch (ex: IllegalArgumentException) {
                // ignore if the receiver is not registered.
            }

            mService.stopForeground(true)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received intent with action $action")
        when (action) {
            ACTION_PAUSE -> mTransportControls.pause()
            ACTION_PLAY -> mTransportControls.play()
            ACTION_NEXT -> mTransportControls.skipToNext()
            ACTION_PREV -> mTransportControls.skipToPrevious()
            else -> Log.w(TAG, "Unknown intent ignored. Action=$action")
        }
    }

    /**
     * Update the state based on a change on the session token. Called either when
     * we are running for the first time or when the media session owner has destroyed the session
     * (see [android.media.session.MediaController.Callback.onSessionDestroyed])
     */
    @Throws(RemoteException::class)
    private fun updateSessionToken() {
        mService.sessionToken?.let {
            if (mSessionToken == null || mSessionToken != null && mSessionToken != it) {
                mController?.unregisterCallback(mCb)
                mSessionToken = it
                val controller = MediaControllerCompat(mService, it)
                mController = controller
                mTransportControls = controller.transportControls
                if (mStarted) {
                    controller.registerCallback(mCb)
                }
            }
        }
    }

    private fun createContentIntent(description: MediaDescriptionCompat?): PendingIntent {
        val openUI = Intent(mService, MusicPlayerActivity::class.java)
        openUI.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        openUI.putExtra(MusicPlayerActivity.EXTRA_START_FULLSCREEN, true)
        if (description != null) {
            openUI.putExtra(MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION, description)
        }
        return PendingIntent.getActivity(mService, REQUEST_CODE, openUI,
                PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private fun createNotification(): Notification? {
        val actions = mPlaybackState?.actions ?: return null
        val description = mMetadata?.description ?: return null

        // Notification channels are only supported on Android O+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val notificationBuilder = NotificationCompat.Builder(mService, CHANNEL_ID)
        var playPauseButtonPosition = 0

        // If skip to previous action is enabled
        if (actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L) {
            notificationBuilder.addAction(R.drawable.ic_round_skip_previous_24,
                    mService.getString(R.string.label_previous), mPreviousIntent)

            // If there is a "skip to previous" button, the play/pause button will
            // be the second one. We need to keep track of it, because the MediaStyle notification
            // requires to specify the index of the buttons (actions) that should be visible
            // when in compact view.
            playPauseButtonPosition = 1
        }

        addPlayPauseAction(notificationBuilder)

        // If skip to next action is enabled
        if (actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L) {
            notificationBuilder.addAction(R.drawable.ic_round_skip_next_24,
                    mService.getString(R.string.label_next), mNextIntent)
        }

        notificationBuilder
                .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(
                                playPauseButtonPosition)  // show only play/pause in compact view
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(mStopIntent)
                        .setMediaSession(mSessionToken))
                .setDeleteIntent(mStopIntent)
                .setColor(mNotificationColor)
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(true)
                .setContentIntent(createContentIntent(description))
                .setContentTitle(description.title)
                .setContentText(description.subtitle)
                .setLargeIcon(description.iconBitmap)

        setNotificationPlaybackState(notificationBuilder)

        return notificationBuilder.build()
    }

    /**
     * Creates Notification Channel. This is required in Android O+ to display notifications.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (mNotificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val mChannel = NotificationChannel(CHANNEL_ID,
                    mService.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW)
            mNotificationManager.createNotificationChannel(mChannel)
        }
    }

    private fun addPlayPauseAction(builder: NotificationCompat.Builder) {
        val label: String
        val icon: Int
        val intent: PendingIntent
        if (mPlaybackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            label = mService.getString(R.string.label_pause)
            icon = R.drawable.ic_round_pause_24
            intent = mPauseIntent
        } else {
            label = mService.getString(R.string.label_play)
            icon = R.drawable.ic_round_play_arrow_24
            intent = mPlayIntent
        }
        builder.addAction(NotificationCompat.Action(icon, label, intent))
    }

    private fun setNotificationPlaybackState(builder: NotificationCompat.Builder) {
        if (mPlaybackState == null) {
            mService.stopForeground(true)
            return
        }
        mPlaybackState?.let {
            if (it.state == PlaybackStateCompat.STATE_PLAYING && it.position >= 0) {
                builder
                        .setWhen(System.currentTimeMillis() - it.position)
                        .setShowWhen(true)
                        .setUsesChronometer(true)
            } else {
                builder
                        .setWhen(0)
                        .setShowWhen(false)
                        .setUsesChronometer(false)
            }

            // Make sure that the notification can be dismissed by the user when we are not playing:
            builder.setOngoing(it.state == PlaybackStateCompat.STATE_PLAYING)
        }
    }

    companion object {
        private const val ACTION_PAUSE = "com.tiefensuche.soundcrowd.pause"
        private const val ACTION_PLAY = "com.tiefensuche.soundcrowd.play"
        private const val ACTION_PREV = "com.tiefensuche.soundcrowd.prev"
        private const val ACTION_NEXT = "com.tiefensuche.soundcrowd.next"
        private const val ACTION_STOP = "com.tiefensuche.soundcrowd.stop"
        private const val REQUEST_CODE = 100
        private const val CHANNEL_ID = "com.tiefensuche.soundcrowd.CHANNEL_ID"
        private val TAG = MediaNotificationManager::class.simpleName
        private const val NOTIFICATION_ID = 412
    }
}