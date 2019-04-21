/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui

import android.app.Activity
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.database.DatabaseHelper
import com.tiefensuche.soundcrowd.playback.PlaybackManager.Companion.CUSTOM_ACTION_PLAY_SEEK
import com.tiefensuche.soundcrowd.utils.LogHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_MUSICS_CUE_POINTS
import com.tiefensuche.soundcrowd.waveform.CuePoint
import java.util.*

/**
 * Fragment that lists all the [CuePoint] items that were created by the user
 *
 * Created by tiefensuche on 6/19/17.
 */
class CueListFragment : Fragment() {
    private lateinit var adapter: ArrayAdapter<CuePoint>

    private val cuePoints = ArrayList<CuePoint>()
    private lateinit var mNoMediaView: View
    private lateinit var mMediaFragmentListener: MediaBrowserFragment.MediaFragmentListener


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_cue_list, container, false)
        val listView = rootView.findViewById<ListView>(R.id.cue_list_view)
        mNoMediaView = rootView.findViewById(R.id.error_no_media)

        activity?.let {
            adapter = ArrayAdapter(it, android.R.layout.simple_list_item_1, cuePoints)
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, i, _ ->
                val point = listView.getItemAtPosition(i)
                if (point is CuePoint && MediaControllerCompat.getMediaController(it) != null) {
                    val bundle = Bundle()
                    bundle.putString("mediaId", point.mediaId)
                    bundle.putInt("position", point.position)
                    MediaControllerCompat.getMediaController(it).transportControls.sendCustomAction(CUSTOM_ACTION_PLAY_SEEK, bundle)
                }
            }
        }

        return rootView
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        if (activity is MediaBrowserFragment.MediaFragmentListener) {
            mMediaFragmentListener = activity
        }
    }

    override fun onStart() {
        super.onStart()
        val mediaBrowser = mMediaFragmentListener.mediaBrowser
        if (mediaBrowser?.isConnected == true) {
            loadItems()
        }
        mMediaFragmentListener.setToolbarTitle(getString(R.string.drawer_cue_points_title))
        mMediaFragmentListener.enableCollapse(false)
        mMediaFragmentListener.showSearchButton(true)
    }

    fun loadItems() {
        mMediaFragmentListener.mediaBrowser?.unsubscribe(MEDIA_ID_MUSICS_CUE_POINTS)
        mMediaFragmentListener.mediaBrowser?.subscribe(MEDIA_ID_MUSICS_CUE_POINTS, object : MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
                cuePoints.clear()
                for (item in children) {
                    item.mediaId?.let { musicId ->
                        item.description.mediaId?.let {
                            if (!MediaIDHelper.isBrowseable(it)) {
                                for (cuePoint in DatabaseHelper.instance.getCuePoints(MediaIDHelper.extractMusicIDFromMediaID(it))) {
                                    cuePoint.mediaId = musicId
                                    cuePoint.description = item.description.subtitle.toString() + " - " + item.description.title.toString() + " @ " + DateUtils.formatElapsedTime((cuePoint.position / 1000).toLong()) + if (cuePoint.description != null) ": " + cuePoint.description else ""
                                    cuePoints.add(cuePoint)
                                }
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
                mNoMediaView.visibility = if (cuePoints.isEmpty()) View.VISIBLE else View.GONE
            }
        })
    }

    fun setFilter(filter: CharSequence?) {
        adapter.filter.filter(filter)
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(CueListFragment::class.java)
    }
}
