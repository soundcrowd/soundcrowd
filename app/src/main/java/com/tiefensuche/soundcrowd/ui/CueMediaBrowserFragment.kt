package com.tiefensuche.soundcrowd.ui

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.playback.PlaybackManager.Companion.CUSTOM_ACTION_PLAY_SEEK
import com.tiefensuche.soundcrowd.playback.PlaybackManager.Companion.CUSTOM_ACTION_REMOVE_CUE_POINT
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Cues.POSITION
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.CUE_POINTS
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.extractMusicIDFromMediaID

internal class CueMediaBrowserFragment : CollectionMediaBrowserFragment() {

    override val mediaId = CUE_POINTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBrowserAdapter =
            CueListItemAdapter(requests, object : CueListItemAdapter.OnItemClickListener {
                override fun onItemClick(item: MediaBrowserCompat.MediaItem, position: Long) {
                    val bundle = Bundle()
                    bundle.putString(MEDIA_ID, item.mediaId)
                    bundle.putLong(POSITION, position)
                    activity?.let {
                        MediaControllerCompat.getMediaController(it).transportControls.sendCustomAction(
                            CUSTOM_ACTION_PLAY_SEEK,
                            bundle
                        )
                    }
                }

                override fun onItemDelete(item: MediaBrowserCompat.MediaItem, position: Long) {
                    activity?.let {
                        val bundle = Bundle()
                        bundle.putString(MEDIA_ID, extractMusicIDFromMediaID(item.mediaId!!))
                        bundle.putInt(POSITION, position.toInt())
                        MediaControllerCompat.getMediaController(it).transportControls.sendCustomAction(
                            CUSTOM_ACTION_REMOVE_CUE_POINT,
                            bundle
                        )
                    }
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