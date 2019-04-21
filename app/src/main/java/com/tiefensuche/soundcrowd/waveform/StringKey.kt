/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.waveform

import com.bumptech.glide.load.Key

import java.security.MessageDigest

/**
 * Created by tiefensuche on 6/29/17.
 */

class StringKey(val key: String) : Key {

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(key.toByteArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as? StringKey

        return key == that?.key
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }
}
