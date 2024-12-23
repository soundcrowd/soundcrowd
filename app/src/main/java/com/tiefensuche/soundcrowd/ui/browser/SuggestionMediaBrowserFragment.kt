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
import com.tiefensuche.soundcrowd.ui.browser.adapters.SuggestionItemAdapter
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.CATEGORY_SEPARATOR

internal open class SuggestionMediaBrowserFragment : MediaBrowserFragment() {

    private var query: CharSequence = ""
    override val mediaId: String
        get() = super.mediaId + CATEGORY_SEPARATOR + query

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBrowserAdapter = SuggestionItemAdapter(object : SuggestionItemAdapter.OnItemClickListener {
            override fun onItemClick(item: MediaItem) {
                mMediaFragmentListener.setQuery(item.mediaMetadata.title, true)
            }
            override fun onItemInsert(item: MediaItem) {
                mMediaFragmentListener.setQuery(item.mediaMetadata.title, false)
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)

        mRecyclerView.adapter = mBrowserAdapter
        mRecyclerView.layoutManager = LinearLayoutManager(rootView.context)

        return rootView
    }

    internal fun setQuery(query: CharSequence?) {
        mBrowserAdapter.clear()
        this.query = query ?: ""
        requestMedia()
    }
}