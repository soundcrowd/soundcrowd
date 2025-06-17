/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import androidx.media3.session.MediaBrowser

internal interface MediaBrowserProvider {

    val mediaBrowser: MediaBrowser
    val connected: Boolean
}