/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.utils

import android.content.Context
import android.net.Uri
import android.support.v4.app.FragmentActivity
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.text.TextUtils
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ARTIST
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_ROOT
import java.util.*

/**
 * Utility class to help on queue related tasks.
 */
object QueueHelper {

    private val TAG = LogHelper.makeLogTag(QueueHelper::class.java)

    private const val RANDOM_QUEUE_SIZE = 10


    fun getPlayingQueue(mediaId: String,
                        musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {

        // extract the browsing hierarchy from the media ID:
        val hierarchy = MediaIDHelper.getHierarchy(mediaId)

        val categoryType = hierarchy[0]
        LogHelper.d(TAG, "Creating playing queue for ", categoryType)

        val tracks = musicProvider.getMusicByCategory(MediaIDHelper.getPath(mediaId))

        val alphabeticalComparator = { o1: MediaMetadataCompat, o2: MediaMetadataCompat -> o1.description.title?.toString()?.compareTo(o2.description.title?.toString() ?: "", ignoreCase = true) ?: 0 }

        for (category in arrayOf(MEDIA_ID_ROOT, MEDIA_ID_MUSICS_BY_ARTIST, MEDIA_ID_MUSICS_BY_ALBUM)) {
            if (category == categoryType) {
                Collections.sort(tracks, alphabeticalComparator)
            }
        }

        return convertToQueue(tracks, MediaIDHelper.getPath(mediaId))
    }

    fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>,
                             mediaId: String): Int {
        for ((index, item) in queue.withIndex()) {
            if (mediaId == item.description.mediaId) {
                return index
            }
        }
        return -1
    }

    fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>,
                             uri: Uri): Int {
        for ((index, item) in queue.withIndex()) {
            if (uri == item.description.mediaUri) {
                return index
            }
        }
        return -1
    }

    fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>,
                             queueId: Long): Int {
        for ((index, item) in queue.withIndex()) {
            if (queueId == item.queueId) {
                return index
            }
        }
        return -1
    }


    private fun convertToQueue(
            tracks: Iterable<MediaMetadataCompat>, vararg categories: String): List<MediaSessionCompat.QueueItem> {
        val queue = ArrayList<MediaSessionCompat.QueueItem>()
        for ((count, track) in tracks.withIndex()) {

            // We create a hierarchy-aware mediaID, so we know what the queue is about by looking
            // at the QueueItem media IDs.
            val hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                    track.description.mediaId, *categories)

            val trackCopy = MediaMetadataCompat.Builder(track)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                    .build()

            // We don't expect queues to change after created, so we use the item index as the
            // queueId. Any other number unique in the queue would work.

            val item = MediaSessionCompat.QueueItem(Utils.getExtendedDescription(trackCopy), count.toLong())
            queue.add(item)
        }
        return queue

    }

    /**
     * Create a random queue with at most [.RANDOM_QUEUE_SIZE] elements.
     *
     * @param musicProvider the provider used for fetching music.
     * @return list containing [MediaSessionCompat.QueueItem]'s
     */

    fun getRandomQueue(musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {
        val result = ArrayList<MediaMetadataCompat>(RANDOM_QUEUE_SIZE)
        val shuffled = musicProvider.shuffledMusic
        for (metadata in shuffled) {
            if (result.size == RANDOM_QUEUE_SIZE) {
                break
            }
            result.add(metadata)
        }
        LogHelper.d(TAG, "getRandomQueue: result.size=", result.size)

        return convertToQueue(result, MEDIA_ID_MUSICS_BY_SEARCH, "random")
    }

    fun isIndexPlayable(index: Int, queue: List<MediaSessionCompat.QueueItem>?): Boolean {
        return queue != null && index >= 0 && index < queue.size
    }

    /**
     * Determine if two queues contain identical media id's in order.
     *
     * @param list1 containing [MediaSessionCompat.QueueItem]'s
     * @param list2 containing [MediaSessionCompat.QueueItem]'s
     * @return boolean indicating whether the queue's match
     */
    fun equals(list1: List<MediaSessionCompat.QueueItem>?,
               list2: List<MediaSessionCompat.QueueItem>?): Boolean {
        if (list1 === list2) {
            return true
        }
        if (list1 == null || list2 == null) {
            return false
        }
        if (list1.size != list2.size) {
            return false
        }
        for (i in list1.indices) {
            if (list1[i].queueId != list2[i].queueId) {
                return false
            }
            if (!TextUtils.equals(list1[i].description.mediaId,
                            list2[i].description.mediaId)) {
                return false
            }
        }
        return true
    }

    /**
     * Determine if queue item matches the currently playing queue item
     *
     * @param context   for retrieving the [MediaControllerCompat]
     * @param queueItem to compare to currently playing [MediaSessionCompat.QueueItem]
     * @return boolean indicating whether queue item matches currently playing queue item
     */
    fun isQueueItemPlaying(context: Context,
                           queueItem: MediaSessionCompat.QueueItem): Boolean {
        // Queue item is considered to be playing or paused based on both the controller's
        // current media id and the controller's active queue item id
        val controller = MediaControllerCompat.getMediaController(context as FragmentActivity)
        if (controller != null && controller.playbackState != null) {
            val currentPlayingQueueId = controller.playbackState.activeQueueItemId
            val currentPlayingMediaId = controller.metadata.description.mediaId
            val itemMusicId = MediaIDHelper.extractMusicIDFromMediaID(queueItem.description.mediaId!!)
            return (queueItem.queueId == currentPlayingQueueId
                    && currentPlayingMediaId != null
                    && TextUtils.equals(currentPlayingMediaId, itemMusicId))
        }
        return false
    }
}
