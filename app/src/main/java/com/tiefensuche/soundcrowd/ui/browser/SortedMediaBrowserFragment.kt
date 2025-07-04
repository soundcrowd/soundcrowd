package com.tiefensuche.soundcrowd.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView

internal open class SortedMediaBrowserFragment : MediaBrowserFragment() {

    override fun onResult(items: List<MediaItem>) {
        super.onResult(items)
        // Create alphabetical sections for root category
        setupIndexScrollBar()
        mBrowserAdapter.notifyDataChanged()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)
        mRecyclerView.setIndexBarVisibility(false)
        return rootView
    }

    private fun setupIndexScrollBar() {
        // initialize as static media content, alphabetically sorted and indexed
        mRecyclerView.setOnTouchListener { _, _ ->
            if (!mBrowserAdapter.isEmpty) {
                mRecyclerView.setIndexBarVisibility(true)
            }
            false
        }
        mRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> mRecyclerView.setIndexBarVisibility(false)
                    RecyclerView.SCROLL_STATE_DRAGGING, RecyclerView.SCROLL_STATE_SETTLING -> if (!mBrowserAdapter.isEmpty) {
                        mRecyclerView.setIndexBarVisibility(true)
                    }
                }
            }
        })
        mBrowserAdapter.generateSections()
    }
}