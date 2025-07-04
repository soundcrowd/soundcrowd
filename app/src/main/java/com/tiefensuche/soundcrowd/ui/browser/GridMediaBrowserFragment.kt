package com.tiefensuche.soundcrowd.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.browser.adapters.GridItemAdapter
import kotlin.math.round

internal open class GridMediaBrowserFragment : SortedMediaBrowserFragment() {

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

        return rootView
    }
}