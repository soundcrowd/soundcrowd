/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.service

import android.content.Context
import android.content.Intent
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.BaseActivity.Companion.MIME_TEXT

/**
 * A simple helper class for sharing content through the Intent.ACTION_SEND paradigm
 */
class Share {
    companion object {
        private val sharingIntent = Intent(Intent.ACTION_SEND)

        fun shareText(rootViewContext : Context, textToShare: String) {
            sharingIntent.type = MIME_TEXT
            sharingIntent.putExtra(Intent.EXTRA_TEXT, textToShare)
            rootViewContext.startActivity(Intent.createChooser(sharingIntent, rootViewContext.resources.getString(
                R.string.share_via)))
        }
    }
}