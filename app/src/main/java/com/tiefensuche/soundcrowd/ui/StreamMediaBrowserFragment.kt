package com.tiefensuche.soundcrowd.ui

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tiefensuche.soundcrowd.R
import kotlin.math.round

internal class StreamMediaBrowserFragment : MediaBrowserFragment() {

    lateinit var mSwipeRefreshLayout: SwipeRefreshLayout

    override fun onResult(action: String, extras: Bundle, resultData: Bundle) {
        mSwipeRefreshLayout.isRefreshing = false
        super.onResult(action, extras, resultData)
        mBrowserAdapter.notifyDataChanged()
    }

    override fun onError(action: String, extras: Bundle, data: Bundle) {
        mSwipeRefreshLayout.isRefreshing = false
        super.onError(action, extras, data)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)
        mBrowserAdapter = GridItemAdapter(requests, object : MediaItemAdapter.OnItemClickListener {
            override fun onItemClick(item: MediaBrowserCompat.MediaItem) {
                mMediaFragmentListener.onMediaItemSelected(item)
            }
        }, ContextCompat.getColor(rootView.context, R.color.colorPrimary))
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
            mBrowserAdapter.notifyDataSetChanged()
            requestMedia(refresh = true)
        }
    }

    override fun setFilter(filter: CharSequence?) {

    }

    companion object {
        private val TAG = this::class.simpleName
    }
}