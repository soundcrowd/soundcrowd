/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.sources

import android.support.v4.media.MediaMetadataCompat
import android.text.TextUtils

/**
 * Holder class that encapsulates a MediaMetadata and allows the actual metadata to be modified
 * without requiring to rebuild the collections the metadata is in.
 */
internal class MutableMediaMetadata(private val trackId: String, var metadata: MediaMetadataCompat) {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || other.javaClass != MutableMediaMetadata::class.java) {
            return false
        }

        val that = other as? MutableMediaMetadata

        return TextUtils.equals(trackId, that?.trackId)
    }

    override fun hashCode(): Int {
        return trackId.hashCode()
    }
}
