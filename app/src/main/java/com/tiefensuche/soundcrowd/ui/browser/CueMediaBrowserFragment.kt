package com.tiefensuche.soundcrowd.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.session.SessionCommand
import androidx.recyclerview.widget.LinearLayoutManager
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_CUE_DELETE
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Cues.POSITION
import com.tiefensuche.soundcrowd.ui.browser.adapters.CueListItemAdapter
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.extractMusicIDFromMediaID

internal class CueMediaBrowserFragment : CollectionMediaBrowserFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBrowserAdapter =
            CueListItemAdapter(requests, object : CueListItemAdapter.OnItemClickListener {
                override fun onItemClick(item: MediaItem, position: Long) {
                    mMediaFragmentListener.onMediaItemSelected(listOf(item), 0, position)
                }

                override fun onItemDelete(item: MediaItem, position: Long) {
                    val bundle = Bundle()
                    bundle.putString(MEDIA_ID, extractMusicIDFromMediaID(item.mediaId))
                    bundle.putInt(POSITION, position.toInt())
                    mMediaFragmentListener.mediaBrowser.sendCustomCommand(SessionCommand(COMMAND_CUE_DELETE, Bundle.EMPTY), bundle)
                }
            })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)

        mRecyclerView.adapter = mBrowserAdapter
        mRecyclerView.layoutManager = LinearLayoutManager(rootView.context)

        return rootView
    }

    override fun updateDescription() {
        mMediaFragmentListener.setToolbarTitle(getString(R.string.drawer_cue_points_title))
        mMediaFragmentListener.enableCollapse(false)
    }
}