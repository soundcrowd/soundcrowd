/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.soundcrowd.ui

import android.app.Activity
import android.os.Bundle
import android.support.v4.media.session.MediaControllerCompat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.playback.PlaybackManager.Companion.CUSTOM_ACTION_PLAY_SEEK
import com.tiefensuche.soundcrowd.service.Database.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.service.Database.Companion.POSITION
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.CUE_POINTS
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.LEAF_SEPARATOR
import com.tiefensuche.soundcrowd.waveform.CuePoint

/**
 * Fragment that lists all the [CuePoint] items that were created by the user
 *
 * Created by tiefensuche on 6/19/17.
 */
internal class CueListFragment : Fragment() {

    private lateinit var adapter: ArrayAdapter<CueItem>
    private lateinit var mNoMediaView: View
    private lateinit var mMediaFragmentListener: MediaBrowserFragment.MediaFragmentListener
    private val cuePoints = ArrayList<CueItem>()

    class CueItem(val cuePoint: CuePoint, val artist: String, val title: String) {
        override fun toString(): String {
            val text = StringBuilder()
                    .append(artist)
                    .append(" - ")
                    .append(title)
                    .append(" @ ")
                    .append(DateUtils.formatElapsedTime((cuePoint.position / 1000).toLong()))

            if (cuePoint.description.isNotEmpty()) {
                text.append(": ").append(cuePoint.description)
            }
            return text.toString()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_cue_list, container, false)
        val listView = rootView.findViewById<ListView>(R.id.cue_list_view)
        mNoMediaView = rootView.findViewById(R.id.error_no_media)

        activity?.let {
            adapter = ArrayAdapter(it, android.R.layout.simple_list_item_1, cuePoints)
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, i, _ ->
                val item = listView.getItemAtPosition(i)
                if (item is CueItem && MediaControllerCompat.getMediaController(it) != null) {
                    val bundle = Bundle()
                    bundle.putString(MEDIA_ID, CUE_POINTS + LEAF_SEPARATOR + item.cuePoint.mediaId)
                    bundle.putLong(POSITION, item.cuePoint.position.toLong())
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
        if (mediaBrowser?.isConnected == true && adapter.isEmpty) {
            loadItems()
        }
        mMediaFragmentListener.setToolbarTitle(getString(R.string.drawer_cue_points_title))
        mMediaFragmentListener.enableCollapse(false)
        mMediaFragmentListener.showSearchButton(true)
    }

    internal fun loadItems() {
        // TODO
    }

    internal fun setFilter(filter: CharSequence?) {
        adapter.filter.filter(filter)
    }
}