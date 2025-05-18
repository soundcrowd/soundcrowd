package com.tiefensuche.soundcrowd.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.browser.adapters.GridItemAdapter
import kotlin.math.round

internal class StreamMediaBrowserFragment : MediaBrowserFragment() {

    private lateinit var mSwipeRefreshLayout: SwipeRefreshLayout

    override fun onResult(items: List<MediaItem>) {
        mSwipeRefreshLayout.isRefreshing = false
        super.onResult(items)
        mBrowserAdapter.notifyDataChanged()
    }

    override fun onError(message: String) {
        mSwipeRefreshLayout.isRefreshing = false
        super.onError(message)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBrowserAdapter = GridItemAdapter(requests, mMediaFragmentListener)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)

        mRecyclerView.adapter = mBrowserAdapter
        mRecyclerView.layoutManager = GridLayoutManager(
            rootView.context,
            round(resources.displayMetrics.widthPixels / (resources.getDimensionPixelSize(R.dimen.media_item_height)).toFloat()).toInt()
        )

        mSwipeRefreshLayout = rootView.findViewById(R.id.swipeContainer)
        mSwipeRefreshLayout.isEnabled = true

        initializeStream()

        return rootView
    }

    private fun initializeStream() {
        // initialize as stream or dynamic media content, ordered as it was added,
        // items can be added when reaching the end
        mRecyclerView.addOnScrollListener(object : EndlessRecyclerViewScrollListener(mRecyclerView.layoutManager as GridLayoutManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView) {
                mProgressBar.visibility = View.VISIBLE
                requestMedia(mBrowserAdapter.count)
            }
        })
        mSwipeRefreshLayout.isEnabled = true
        mSwipeRefreshLayout.setOnRefreshListener {
            mBrowserAdapter.clear()
            requestMedia(refresh = true)
        }
    }
}