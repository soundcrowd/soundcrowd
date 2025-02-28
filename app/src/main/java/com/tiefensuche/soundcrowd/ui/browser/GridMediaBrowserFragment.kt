package com.tiefensuche.soundcrowd.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.GridLayoutManager
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.browser.adapters.GridItemAdapter
import com.tiefensuche.soundcrowd.ui.browser.adapters.MediaItemAdapter
import kotlin.math.round

internal open class GridMediaBrowserFragment : CollectionMediaBrowserFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBrowserAdapter = GridItemAdapter(requests, object : MediaItemAdapter.OnItemClickListener {
            override fun onItemClick(items: List<MediaItem>, position: Int) {
                mMediaFragmentListener.onMediaItemSelected(items, position)
            }
        }, ContextCompat.getColor(requireContext(), R.color.colorPrimary))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)

        mRecyclerView.adapter = mBrowserAdapter
        mRecyclerView.layoutManager = GridLayoutManager(
            rootView.context,
            round(resources.displayMetrics.widthPixels / (resources.getDimensionPixelSize(R.dimen.media_item_height)).toFloat()).toInt()
        )

        return rootView
    }
}