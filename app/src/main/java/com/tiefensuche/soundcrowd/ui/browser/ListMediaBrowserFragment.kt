/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import com.tiefensuche.soundcrowd.ui.browser.adapters.ListItemAdapter
import com.tiefensuche.soundcrowd.ui.browser.adapters.MediaItemAdapter

internal open class ListMediaBrowserFragment : CollectionMediaBrowserFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBrowserAdapter = ListItemAdapter(requests, object : MediaItemAdapter.OnItemClickListener {
            override fun onItemClick(items: List<MediaItem>, position: Int) {
                mMediaFragmentListener.onMediaItemSelected(items, position)
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)

        mRecyclerView.adapter = mBrowserAdapter
        mRecyclerView.layoutManager = LinearLayoutManager(rootView.context)

        return rootView
    }
}