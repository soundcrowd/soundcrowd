/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.support.v4.media.MediaBrowserCompat

internal interface MediaBrowserProvider {

    val mediaBrowser: MediaBrowserCompat?
}
