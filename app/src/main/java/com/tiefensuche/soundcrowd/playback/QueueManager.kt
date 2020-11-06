/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.preference.PreferenceManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LAST_MEDIA
import com.tiefensuche.soundcrowd.utils.MediaIDHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.LEAF_SEPARATOR
import com.tiefensuche.soundcrowd.utils.QueueHelper
import com.tiefensuche.soundcrowd.utils.Utils
import java.util.*

/**
 * Simple data provider for queues. Keeps track of a current queue and a current index in the
 * queue. Also provides methods to set the current queue based on common queries, relying on a
 * given MusicProvider to provide the actual media metadata.
 */
internal class QueueManager(private val mMusicProvider: MusicProvider,
                            internal val mListener: MetadataUpdateListener,
                            private val mContext: Context) {

    // "Now playing" queue:
    private var mPlayingQueue: List<MediaSessionCompat.QueueItem>
    private var mCurrentIndex: Int = 0

    internal val currentMusic: MediaSessionCompat.QueueItem?
        get() = if (QueueHelper.isIndexPlayable(mCurrentIndex, mPlayingQueue))
            mPlayingQueue[mCurrentIndex]
        else
            null

    init {
        mPlayingQueue = Collections.synchronizedList<MediaSessionCompat.QueueItem>(ArrayList<MediaSessionCompat.QueueItem>())
        mCurrentIndex = 0
    }

    private fun isSameBrowsingCategory(mediaId: String): Boolean {
        val newBrowseHierarchy = MediaIDHelper.getHierarchy(mediaId)
        currentMusic?.description?.mediaId?.let {
            val currentBrowseHierarchy = MediaIDHelper.getHierarchy(it)
            return newBrowseHierarchy.contentEquals(currentBrowseHierarchy)
        }
        return false
    }

    private fun setCurrentQueueIndex(index: Int) {
        if (index >= 0 && index < mPlayingQueue.size) {
            mCurrentIndex = index
            mListener.onCurrentQueueIndexUpdated(mCurrentIndex)
        }
    }

    internal fun setCurrentQueueItem(queueId: Long): Boolean {
        // set the current index on queue from the queue Id:
        val index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, queueId)
        setCurrentQueueIndex(index)
        return index >= 0
    }

    private fun setCurrentQueueItem(mediaId: String): Boolean {
        // set the current index on queue from the music Id:
        val index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, mediaId)
        setCurrentQueueIndex(index)
        return index >= 0
    }

    internal fun skipQueuePosition(amount: Int): Boolean {
        if (mPlayingQueue.isEmpty() || mPlayingQueue.size == 1)
            return false
        var index = mCurrentIndex + amount
        if (index < 0) {
            // skip backwards before the first song will keep you on the first song
            index = 0
        } else {
            // skip forwards when in last song will cycle back to start of the queue
            index %= mPlayingQueue.size
        }
        if (!QueueHelper.isIndexPlayable(index, mPlayingQueue))
            return false
        mCurrentIndex = index
        return true
    }

    internal fun setLastItem(): Boolean {
        PreferenceManager.getDefaultSharedPreferences(mContext).getString(LAST_MEDIA, null)?.let {
            setQueueFromMusic(LAST_MEDIA + LEAF_SEPARATOR + it)
            return true
        }
        return false
    }

    internal fun setQueueFromMusic(mediaId: String) {
        // The mediaId used here is not the unique musicId. This one comes from the
        // MediaBrowser, and is actually a "hierarchy-aware mediaID": a concatenation of
        // the hierarchy in MediaBrowser and the actual unique musicID. This is necessary
        // so we can build the correct playing queue, based on where the track was
        // selected from.
        var canReuseQueue = false
        if (isSameBrowsingCategory(mediaId)) {
            canReuseQueue = setCurrentQueueItem(mediaId)
        }
        if (!canReuseQueue) {
            setCurrentQueue(MediaIDHelper.getPath(mediaId),
                    QueueHelper.getPlayingQueue(mediaId, mMusicProvider), mediaId)
        }
        updateMetadata()
    }

    internal fun setQueueFromMusic(uri: Uri) {
        val mediaId = mMusicProvider.resolve(uri)
        if (mediaId != null)
            setQueueFromMusic(mediaId)
    }

    private fun setCurrentQueue(title: String, newQueue: List<MediaSessionCompat.QueueItem>,
                                initialMediaId: String? = null) {
        mPlayingQueue = newQueue
        var index = 0
        if (initialMediaId != null)
            index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, initialMediaId)
        mCurrentIndex = index.coerceAtLeast(0)
        mListener.onQueueUpdated(title, newQueue)
    }

    internal fun updateMetadata() {
        currentMusic?.description?.mediaId?.let {
            update(MediaIDHelper.extractMusicIDFromMediaID(it))
        } ?: mListener.onMetadataRetrieveError()
    }

    internal fun update(musicId: String) {
        val metadata = mMusicProvider.getMusic(musicId)
                ?: throw IllegalArgumentException("Invalid musicId $musicId")

        mListener.onMetadataChanged(metadata)

        // Set the proper album artwork on the media session, so it can be shown in the
        // locked screen and in other places.
        ArtworkHelper.fetch(mContext.applicationContext, metadata.description, ArtworkHelper.MAX_ART_WIDTH, ArtworkHelper.MAX_ART_HEIGHT, object : ArtworkHelper.FetchListener {
            override fun onFetched(image: Bitmap) {
                setArtwork(musicId, image)
            }
        })
    }

    private fun setArtwork(musicId: String, bitmap: Bitmap) {
        val icon = Utils.scaleBitmap(bitmap, ArtworkHelper.MAX_ART_WIDTH_ICON, ArtworkHelper.MAX_ART_HEIGHT_ICON)
        mMusicProvider.updateMusicArt(musicId, bitmap, icon)

        // If we are still playing the same music, notify the listeners:
        currentMusic?.description?.mediaId?.let {
            val currentPlayingId = MediaIDHelper.extractMusicIDFromMediaID(it)
            if (musicId == currentPlayingId)
                mListener.onMetadataChanged(mMusicProvider.getMusic(currentPlayingId))
        }
    }

    interface MetadataUpdateListener {
        fun onMetadataChanged(metadata: MediaMetadataCompat?)
        fun onMetadataRetrieveError()
        fun onCurrentQueueIndexUpdated(queueIndex: Int)
        fun onQueueUpdated(title: String, newQueue: List<MediaSessionCompat.QueueItem>)
    }
}