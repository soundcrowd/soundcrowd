/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Point
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.service.MusicService.Companion.ARG_ERROR
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.ACTION_GET_MEDIA
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.RESULT
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LOCAL
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.NAME
import com.tiefensuche.soundcrowd.ui.intro.ShowcaseViewManager
import `in`.myinnos.alphabetsindexfastscrollrecycler.IndexFastScrollRecyclerView

/**
 * A Fragment that lists all the various browsable queues available
 * from a [android.service.media.MediaBrowserService].
 *
 *
 * It uses a [MediaBrowserCompat] to connect to the [MusicService].
 * Once connected, the fragment subscribes to get all the children.
 * All [MediaBrowserCompat.MediaItem]'s that can be browsed are shown in a ListView.
 */
internal abstract class MediaBrowserFragment : Fragment() {

    lateinit var mBrowserAdapter: MediaItemAdapter<*>
    lateinit var mMediaFragmentListener: MediaFragmentListener
    private lateinit var mNoMediaView: TextView
    lateinit var mProgressBar: ProgressBar
    lateinit var mRecyclerView: IndexFastScrollRecyclerView
    lateinit var requests: GlideRequests

    open fun onResult(action: String, extras: Bundle, resultData: Bundle) {
        Log.d(TAG, "fragment onResult, action=$action, count=${resultData.size()}")

        // hide loading indicators
        mProgressBar.visibility = View.GONE

        resultData.getParcelableArrayList<MediaBrowserCompat.MediaItem>(RESULT)
            ?.forEach { mBrowserAdapter.add(it) }

        showNoMedia(mBrowserAdapter.isEmpty)
        activity?.let {
            val view = it.findViewById<View>(R.id.toolbar)
            ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.SEARCH_VIEW, Point(view.width - view.height / 2, view.height / 2), it)
        }
    }

    open fun onError(action: String, extras: Bundle, data: Bundle) {
        Log.e(TAG, "browse fragment onError, id=$id")

        // hide loading indicators
        mProgressBar.visibility = View.GONE
        data.getString(ARG_ERROR)?.let {
            showMessage(it)
        }
    }

    internal open val mediaId: String
        get() = arguments?.getString(MEDIA_ID)
                ?: arguments?.getParcelable<MediaDescriptionCompat>(ARG_MEDIA_DESCRIPTION)?.mediaId
                ?: DEFAULT_MEDIA_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requests = GlideApp.with(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = inflater.inflate(R.layout.fragment_list, container, false)

        mNoMediaView = rootView.findViewById(R.id.error_no_media)
        mProgressBar = rootView.findViewById(R.id.progressBar)

        mRecyclerView = rootView.findViewById(R.id.list_view)
        mRecyclerView.setIndexBarVisibility(false)

        mMediaFragmentListener.showSearchButton(true)

        // update toolbar title, collapsing toolbar layout
        updateDescription()

        return rootView
    }

    internal open fun setFilter(filter: CharSequence?) {
        mBrowserAdapter.filter.filter(filter)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        if (activity is MediaFragmentListener)
            mMediaFragmentListener = activity
    }

    fun requestMedia(offset: Int = 0, refresh: Boolean = false) {
        val bundle = Bundle()

        if (offset > 0)
            bundle.putInt(MusicProvider.OFFSET, mBrowserAdapter.count)

        if (refresh)
            bundle.putBoolean(MusicProvider.OPTION_REFRESH, true)

        bundle.putString(MEDIA_ID, mediaId)
        mMediaFragmentListener.mediaBrowser?.sendCustomAction(ACTION_GET_MEDIA, bundle, object : MediaBrowserCompat.CustomActionCallback() {
            override fun onResult(action: String, extras: Bundle, resultData: Bundle) {
                this@MediaBrowserFragment.onResult(action, extras, resultData)
            }

            override fun onError(action: String, extras: Bundle, data: Bundle) {
                this@MediaBrowserFragment.onError(action, extras, data)
            }
        })
    }

    override fun onStart() {
        super.onStart()

        // fetch browsing information to fill the listview:
        val mediaBrowser = mMediaFragmentListener.mediaBrowser

        Log.d(TAG, "fragment.onStart, mediaId=$mediaId, onConnected=${mediaBrowser?.isConnected}")

        if (mediaBrowser?.isConnected == true && mBrowserAdapter.isEmpty)
            requestMedia()
    }

    override fun onStop() {
        super.onStop()
        val mediaBrowser = mMediaFragmentListener.mediaBrowser
        if (mediaBrowser != null && mediaBrowser.isConnected)
            mediaBrowser.unsubscribe(mediaId)
    }

    fun showMessage(text: String) {
        mNoMediaView.text = text
        showNoMedia(true)
    }

    private fun showNoMedia(show: Boolean) {
        mNoMediaView.visibility = if (show) View.VISIBLE else View.GONE
    }

    open fun updateDescription() {
        arguments?.let {
            it.getParcelable<MediaDescriptionCompat>(ARG_MEDIA_DESCRIPTION)?.let {
                mMediaFragmentListener.enableCollapse(true)
                mMediaFragmentListener.setToolbarTitle(it.title)
                mMediaFragmentListener.setSubtitle(it.subtitle)
                mMediaFragmentListener.setBackground(it)
                return
            } ?: mMediaFragmentListener.setToolbarTitle(it.getString(NAME))
        }
        mMediaFragmentListener.enableCollapse(false)
    }

    internal interface MediaFragmentListener : MediaBrowserProvider {
        fun onMediaItemSelected(item: MediaBrowserCompat.MediaItem)
        fun setToolbarTitle(title: CharSequence?)
        fun setSubtitle(title: CharSequence?)
        fun setBackground(description: MediaDescriptionCompat)
        fun enableCollapse(enable: Boolean)
        fun showSearchButton(show: Boolean)
    }

    companion object {
        private val TAG = MediaBrowserFragment::class.simpleName
        internal const val ARG_MEDIA_DESCRIPTION = "media_description"
        internal const val DEFAULT_MEDIA_ID = LOCAL
    }
}