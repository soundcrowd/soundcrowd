/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import `in`.myinnos.alphabetsindexfastscrollrecycler.IndexFastScrollRecyclerView
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.service.MusicService.Companion.ARG_ERROR
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.ACTION_GET_MEDIA
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.RESULT
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LOCAL
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.MEDIA_TYPE
import com.tiefensuche.soundcrowd.ui.intro.ShowcaseViewManager

/**
 * A Fragment that lists all the various browsable queues available
 * from a [android.service.media.MediaBrowserService].
 *
 *
 * It uses a [MediaBrowserCompat] to connect to the [MusicService].
 * Once connected, the fragment subscribes to get all the children.
 * All [MediaBrowserCompat.MediaItem]'s that can be browsed are shown in a ListView.
 */
internal class MediaBrowserFragment : Fragment() {

    private lateinit var mBrowserAdapter: MediaItemAdapter
    private lateinit var mMediaFragmentListener: MediaFragmentListener
    private lateinit var mNoMediaView: TextView
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mSwipeRefreshLayout: SwipeRefreshLayout
    private lateinit var mRecyclerView: IndexFastScrollRecyclerView
    private lateinit var requests: GlideRequests

    private val mCallback = object : MediaBrowserCompat.CustomActionCallback() {
        override fun onResult(action: String, extras: Bundle, resultData: Bundle) {
            Log.d(TAG, "fragment onResult, action=$action, count=${resultData.size()}")

            // hide loading indicators
            mSwipeRefreshLayout.isRefreshing = false
            mProgressBar.visibility = View.GONE

            for (item in resultData.getParcelableArrayList<MediaBrowserCompat.MediaItem>(RESULT)) {
                mBrowserAdapter.add(item)
            }
            // For stream update dataset directly, otherwise sort and create index
            if (isStream) mBrowserAdapter.notifyDataSetChanged() else mBrowserAdapter.notifyDataChanged()

            showNoMedia(mBrowserAdapter.isEmpty)

            activity?.let {
                val view = it.findViewById<View>(R.id.toolbar)
                ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.SEARCH_VIEW, Point(view.width - view.height / 2, view.height / 2), it)
            }
        }

        override fun onError(action: String, extras: Bundle, data: Bundle) {
            Log.e(TAG, "browse fragment onError, id=$id")

            // hide loading indicators
            mSwipeRefreshLayout.isRefreshing = false
            mProgressBar.visibility = View.GONE
            data.getString(ARG_ERROR)?.let {
                showMessage(it)
            }
        }
    }

    internal val mediaId: String
        get() = arguments?.getString(MEDIA_ID)
                ?: arguments?.getParcelable<MediaDescriptionCompat>(ARG_MEDIA_DESCRIPTION)?.mediaId
                ?: DEFAULT_MEDIA_ID

    internal val isStream: Boolean
        get() = arguments?.getString(MEDIA_TYPE) == MediaMetadataCompatExt.MediaType.STREAM.name
                || arguments?.getParcelable<MediaDescriptionCompat>(ARG_MEDIA_DESCRIPTION)?.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE) == MediaMetadataCompatExt.MediaType.STREAM.name

    private fun calculateNoOfColumns(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        val scalingFactor = (resources.getDimensionPixelSize(R.dimen.media_item_height) / displayMetrics.density).toInt()
        return (dpWidth / scalingFactor).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_list, container, false)

        requests = GlideApp.with(this)
        mBrowserAdapter = MediaItemAdapter(requests, object : MediaItemAdapter.OnItemClickListener {
            override fun onItemClick(item: MediaBrowserCompat.MediaItem) {
                mMediaFragmentListener.onMediaItemSelected(item)
            }
        }, ContextCompat.getColor(rootView.context, R.color.colorPrimary))
        mNoMediaView = rootView.findViewById(R.id.error_no_media)
        mProgressBar = rootView.findViewById(R.id.progressBar)

        mSwipeRefreshLayout = rootView.findViewById(R.id.swipeContainer)
        mRecyclerView = rootView.findViewById(R.id.list_view)
        val mLayoutManager = GridLayoutManager(
            rootView.context,
            calculateNoOfColumns(rootView.context)
        )
        mRecyclerView.layoutManager = mLayoutManager
        mRecyclerView.adapter = mBrowserAdapter

        // initially hide index bar (not possible in layout)
        mRecyclerView.setIndexBarVisibility(false)

        // initialize as static media content, alphabetically sorted and indexed
        if (!isStream) {
            mSwipeRefreshLayout.isEnabled = false
            mRecyclerView.setOnTouchListener { _, _ ->
                if (!mBrowserAdapter.isEmpty) {
                    mRecyclerView.setIndexBarVisibility(true)
                }
                false
            }
            mRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> mRecyclerView.setIndexBarVisibility(false)
                        RecyclerView.SCROLL_STATE_DRAGGING, RecyclerView.SCROLL_STATE_SETTLING -> if (!mBrowserAdapter.isEmpty) {
                            mRecyclerView.setIndexBarVisibility(true)
                        }
                    }
                }
            })
        } else {
            // initialize as stream or dynamic media content, ordered as it was added,
            // items can be added when reaching the end
            mRecyclerView.addOnScrollListener(object : EndlessRecyclerViewScrollListener(mLayoutManager) {
                override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView) {
                    mProgressBar.visibility = View.VISIBLE
                    requestMedia(mBrowserAdapter.count)
                }
            })
            mSwipeRefreshLayout.setOnRefreshListener {
                mBrowserAdapter.clear()
                mBrowserAdapter.notifyDataSetChanged()
                requestMedia(refresh = true)
            }
        }

        mMediaFragmentListener.showSearchButton(true)

        // update toolbar title, collapsing toolbar layout
        updateDescription()

        return rootView
    }

    internal fun setFilter(filter: CharSequence?) {
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

        bundle.putString(MEDIA_ID, arguments?.getString(MEDIA_ID) ?: LOCAL)
        mMediaFragmentListener.mediaBrowser?.sendCustomAction(ACTION_GET_MEDIA, bundle, mCallback)
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

    private fun updateDescription() {
        mMediaFragmentListener.setToolbarTitle(if (mediaId == LOCAL) getString(R.string.drawer_allmusic_title) else mediaId)
        activity?.let { activity ->
            arguments?.getParcelable<MediaDescriptionCompat>(ARG_MEDIA_DESCRIPTION)?.let {
                ArtworkHelper.loadArtwork(requests, it, activity.findViewById(R.id.container_image))
                (activity.findViewById<View>(R.id.header_line2) as? TextView)?.text = it.subtitle
                mMediaFragmentListener.enableCollapse(true)
                mMediaFragmentListener.setToolbarTitle(it.title)
            } ?: mMediaFragmentListener.enableCollapse(false)
        }
    }

    internal interface MediaFragmentListener : MediaBrowserProvider {
        fun onMediaItemSelected(item: MediaBrowserCompat.MediaItem)
        fun setToolbarTitle(title: CharSequence?)
        fun enableCollapse(enable: Boolean)
        fun showSearchButton(show: Boolean)
    }

    companion object {
        private val TAG = MediaBrowserFragment::class.simpleName
        internal const val ARG_MEDIA_DESCRIPTION = "media_description"
        internal const val DEFAULT_MEDIA_ID = LOCAL
    }
}