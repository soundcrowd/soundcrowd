/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.utils

import java.util.*

/**
 * Utility class to help on queue related tasks.
 */
object MediaIDHelper {

    // Media IDs used on browseable items of MediaBrowser
    const val MEDIA_ID_ROOT = "__ROOT__"
    const val MEDIA_ID_PLUGINS = "__PLUGINS__"
    const val MEDIA_ID_MUSICS_BY_SEARCH = "__BY_SEARCH__"
    const val MEDIA_ID_MUSICS_BY_ARTIST = "__BY_ARTIST__"
    const val MEDIA_ID_MUSICS_BY_ALBUM = "__BY_ALBUM__"

    const val MEDIA_ID_MUSICS_CUE_POINTS = "__CUE_POINTS__"
    const val MEDIA_ID_TEMP = "__TEMP__"

    const val CATEGORY_SEPARATOR = '/'
    const val LEAF_SEPARATOR = '|'

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
    fun createMediaID(musicID: String?, vararg categories: String): String {
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

    fun extractMusicIDFromMediaID(mediaID: String): String {
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
    fun getHierarchy(mediaID: String): Array<String> {
        var mediaID = mediaID
        val pos = mediaID.indexOf(LEAF_SEPARATOR)
        if (pos >= 0) {
            mediaID = mediaID.substring(0, pos)
        }
        return mediaID.split(CATEGORY_SEPARATOR.toString().toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    }

    fun extractBrowseCategoryValueFromMediaID(mediaID: String): String? {
        val hierarchy = getHierarchy(mediaID)
        return if (hierarchy.size == 2) {
            hierarchy[1]
        } else null
    }

    fun isBrowseable(mediaID: String): Boolean {
        return mediaID.indexOf(LEAF_SEPARATOR) < 0
    }


    fun getParentMediaID(mediaID: String): String {
        val hierarchy = getHierarchy(mediaID)
        if (!isBrowseable(mediaID)) {
            return createMediaID(null, *hierarchy)
        }
        if (hierarchy.size <= 1) {
            return MEDIA_ID_ROOT
        }
        val parentHierarchy = Arrays.copyOf(hierarchy, hierarchy.size - 1)
        return createMediaID(null, *parentHierarchy)
    }

    fun getPath(mediaID: String): String {
        return mediaID.substring(0, mediaID.indexOf(LEAF_SEPARATOR))
    }
}
