/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.utils

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.text.TextUtils
import android.util.Log
import com.tiefensuche.soundcrowd.sources.MusicProvider

/**
 * Utility class to help on queue related tasks.
 */
internal object QueueHelper {

    private val TAG = QueueHelper::class.simpleName

    internal fun getPlayingQueue(mediaId: String,
                                 musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {
        // extract the browsing hierarchy from the media ID:
        val hierarchy = MediaIDHelper.getHierarchy(mediaId)
        Log.d(TAG, "Creating playing queue for ${hierarchy[0]}")

        return convertToQueue(musicProvider.getMusicByCategory(MediaIDHelper.getPath(mediaId)),
                MediaIDHelper.getPath(mediaId))
    }

    internal fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>,
                                      mediaId: String): Int {
        for ((index, item) in queue.withIndex()) {
            if (mediaId == item.description.mediaId) {
                return index
            }
        }
        return -1
    }

    internal fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>,
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

    internal fun isIndexPlayable(index: Int, queue: List<MediaSessionCompat.QueueItem>?): Boolean {
        return queue != null && index >= 0 && index < queue.size
    }

    /**
     * Determine if two queues contain identical media id's in order.
     *
     * @param list1 containing [MediaSessionCompat.QueueItem]'s
     * @param list2 containing [MediaSessionCompat.QueueItem]'s
     * @return boolean indicating whether the queue's match
     */
    internal fun equals(list1: List<MediaSessionCompat.QueueItem>?,
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
}