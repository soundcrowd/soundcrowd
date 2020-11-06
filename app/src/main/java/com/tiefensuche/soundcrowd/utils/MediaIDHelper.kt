/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.utils

import java.util.*

/**
 * Utility class to help on queue related tasks.
 */
internal object MediaIDHelper {

    // Media IDs used on browseable items of MediaBrowser
    internal const val CATEGORY_SEPARATOR = '/'
    internal const val LEAF_SEPARATOR = '|'

    /**
     * Create a String value that represents a playable or a browsable media.
     *
     *
     * Encode the media browseable categories, if any, and the unique music ID, if any,
     * into a single String mediaID.
     *
     *
     * MediaIDs are of the form <categoryType>/<categoryValue>|<musicUniqueId>, to make it easy
     * to find the category (like genre) that a music was selected from, so we
     * can correctly build the playing queue. This is specially useful when
     * one music can appear in more than one list, like "by genre -> genre_1"
     * and "by artist -> artist_1".
     *
     * @param musicID    Unique music ID for playable items, or null for browseable items.
     * @param categories hierarchy of categories representing this item's browsing parents
     * @return a hierarchy-aware media ID
    </musicUniqueId></categoryValue></categoryType> */
    internal fun createMediaID(musicID: String?, vararg categories: String): String {
        val sb = StringBuilder()
        for (i in categories.indices) {
            sb.append(categories[i])
            if (i < categories.size - 1) {
                sb.append(CATEGORY_SEPARATOR)
            }
        }
        if (musicID != null) {
            sb.append(LEAF_SEPARATOR).append(musicID)
        }
        return sb.toString()
    }

    /**
     * Extracts unique musicID from the mediaID. mediaID is, by this sample's convention, a
     * concatenation of category (eg "by_genre"), categoryValue (eg "Classical") and unique
     * musicID. This is necessary so we know where the user selected the music from, when the music
     * exists in more than one music list, and thus we are able to correctly build the playing queue.
     *
     * @param mediaID that contains the musicID
     * @return musicID
     */

    internal fun extractMusicIDFromMediaID(mediaID: String): String {
        val pos = mediaID.indexOf(LEAF_SEPARATOR)
        return if (pos >= 0) {
            mediaID.substring(pos + 1)
        } else mediaID
    }

    /**
     * Extracts category and categoryValue from the mediaID. mediaID is, by this sample's
     * convention, a concatenation of category (eg "by_genre"), categoryValue (eg "Classical") and
     * mediaID. This is necessary so we know where the user selected the music from, when the music
     * exists in more than one music list, and thus we are able to correctly build the playing queue.
     *
     * @param mediaID that contains a category and categoryValue.
     */
    internal fun getHierarchy(mediaID: String): Array<String> {
        var mediaID = mediaID
        val pos = mediaID.indexOf(LEAF_SEPARATOR)
        if (pos >= 0) {
            mediaID = mediaID.substring(0, pos)
        }
        return mediaID.split(CATEGORY_SEPARATOR).toTypedArray()
    }

    internal fun extractSourceValueFromMediaID(mediaID: String): String? {
        val hierarchy = getHierarchy(mediaID)
        return if (hierarchy.isNotEmpty()) {
            hierarchy[0]
        } else null
    }

    internal fun getPath(mediaID: String): String {
        return mediaID.substring(0, mediaID.indexOf(LEAF_SEPARATOR))
    }

    internal fun toBrowsableName(text: String): String {
        return text.trim().toLowerCase(Locale.ROOT).replace(CATEGORY_SEPARATOR, '-')
    }
}