package com.tiefensuche.soundcrowd.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.HISTORY

internal class HistoryMediaBrowserFragment : ListMediaBrowserFragment() {

    override val mediaId = HISTORY

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)
        return rootView
    }

    override fun updateDescription() {
        mMediaFragmentListener.setToolbarTitle(getString(R.string.drawer_history_title))
        mMediaFragmentListener.enableCollapse(false)
        mMediaFragmentListener.showSearchButton(false)
    }
}