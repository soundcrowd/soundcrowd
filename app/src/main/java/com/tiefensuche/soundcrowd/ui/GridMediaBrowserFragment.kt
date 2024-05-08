package com.tiefensuche.soundcrowd.ui

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.tiefensuche.soundcrowd.R
import kotlin.math.round

internal open class GridMediaBrowserFragment : CollectionMediaBrowserFragment() {
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

        return rootView
    }
}