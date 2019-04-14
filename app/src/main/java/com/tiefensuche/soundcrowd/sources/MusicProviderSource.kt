/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.sources


import android.support.v4.media.MediaMetadataCompat

internal interface MusicProviderSource {

    operator fun iterator(): Iterator<MediaMetadataCompat>

    companion object {
        const val CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__"
        const val CUSTOM_METADATA_WAVEFORM_URL = "__TRACK_WAVEFORM_URL__"
        const val CUSTOM_METADATA_MEDIA_SOURCE = "__MEDIA_SOURCE__"
        const val CUSTOM_METADATA_MEDIA_KIND = "__MEDIA_KIND__"
    }
}
