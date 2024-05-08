/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.tiefensuche.soundcrowd.waveform.CuePoint

/**
 * Fragment that lists all the [CuePoint] items that were created by the user
 *
 * Created by tiefensuche on 6/19/17.
 */
internal open class ListMediaBrowserFragment : CollectionMediaBrowserFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)
        mBrowserAdapter = ListItemAdapter(requests, object : MediaItemAdapter.OnItemClickListener {
            override fun onItemClick(item: MediaBrowserCompat.MediaItem) {
                mMediaFragmentListener.onMediaItemSelected(item)
            }
        })
        mRecyclerView.adapter = mBrowserAdapter
        mRecyclerView.layoutManager = LinearLayoutManager(rootView.context)
        return rootView
    }
    companion object {
        private val TAG = this::class.simpleName
    }
}