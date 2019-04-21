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
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.service.MusicService
import com.tiefensuche.soundcrowd.ui.intro.ShowcaseViewManager
import com.tiefensuche.soundcrowd.utils.LogHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper

/**
 * A Fragment that lists all the various browsable queues available
 * from a [android.service.media.MediaBrowserService].
 *
 *
 * It uses a [MediaBrowserCompat] to connect to the [MusicService].
 * Once connected, the fragment subscribes to get all the children.
 * All [MediaBrowserCompat.MediaItem]'s that can be browsed are shown in a ListView.
 */
class MediaBrowserFragment : Fragment() {
    private lateinit var mBrowserAdapter: MediaItemAdapter

    private var mDescription: MediaDescriptionCompat? = null

    private lateinit var mMediaFragmentListener: MediaFragmentListener
    private lateinit var mNoMediaView: View
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mSwipeRefreshLayout: SwipeRefreshLayout
    private lateinit var mRecyclerView: IndexFastScrollRecyclerView
    private var isStream: Boolean = false
    private val mSubscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String,
                                      children: List<MediaBrowserCompat.MediaItem>,
                                      options: Bundle) {
            LogHelper.d(TAG, "fragment onChildrenLoaded, parentId=" + parentId +
                    "  count=" + children.size)

            // hide loading indicators
            mSwipeRefreshLayout.isRefreshing = false
            mProgressBar.visibility = View.GONE

            for (item in children) {
                mBrowserAdapter.add(item)
            }
            // For stream update dataset directly, otherwise sort and create index
            if (isStream) mBrowserAdapter.notifyDataSetChanged() else mBrowserAdapter.notifyDataChanged()

            updateTitle()
            showNoMedia(mBrowserAdapter.isEmpty)

            activity?.let {
                val view = it.findViewById<View>(R.id.toolbar)
                ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.SEARCH_VIEW, Point(view.width - view.height / 2, view.height / 2), it)
            }

        }

        override fun onError(id: String) {
            LogHelper.e(TAG, "browse fragment subscription onError, id=$id")
        }
    }
    private lateinit var requests: GlideRequests


    var mMediaId: String
        get() {
            val args = arguments
            return args?.getString(ARG_MEDIA_ID) ?: MediaIDHelper.MEDIA_ID_ROOT
        }
        set(mMediaId) {
            val args = Bundle(1)
            args.putString(MediaBrowserFragment.ARG_MEDIA_ID, mMediaId)
            arguments = args
        }

    private fun calculateNoOfColumns(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        val scalingFactor = (resources.getDimensionPixelSize(R.dimen.media_item_height) / displayMetrics.density).toInt()
        return (dpWidth / scalingFactor).toInt()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_list, container, false)

        isStream = arguments?.getBoolean("stream") ?: false

        requests = GlideApp.with(this)
        mBrowserAdapter = MediaItemAdapter(requests, object: MediaItemAdapter.OnItemClickListener {
            override fun onItemClick(item: MediaBrowserCompat.MediaItem) {
                mMediaFragmentListener.onMediaItemSelected(item)
                mMediaFragmentListener.closeSearchMenu()
            }
        }, ContextCompat.getColor(rootView.context, R.color.colorPrimary))
        mNoMediaView = rootView.findViewById(R.id.error_no_media)
        mProgressBar = rootView.findViewById(R.id.progressBar1)

        mSwipeRefreshLayout = rootView.findViewById(R.id.swipeContainer)
        mRecyclerView = rootView.findViewById(R.id.list_view)
        val mLayoutManager = GridLayoutManager(rootView.context, calculateNoOfColumns(rootView.context))
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
                        android.support.v7.widget.RecyclerView.SCROLL_STATE_IDLE -> mRecyclerView.setIndexBarVisibility(false)
                        android.support.v7.widget.RecyclerView.SCROLL_STATE_DRAGGING, android.support.v7.widget.RecyclerView.SCROLL_STATE_SETTLING -> if (!mBrowserAdapter.isEmpty) {
                            mRecyclerView.setIndexBarVisibility(true)
                        }
                    }
                }

            })
        } else {
            // initialize as stream or dynamic media content, ordered as it was added,
            // items can be added when reaching the end
            mRecyclerView.addOnScrollListener(object: EndlessRecyclerViewScrollListener(mLayoutManager) {
                override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView) {
                    mProgressBar.visibility = View.VISIBLE
                    val bundle = Bundle()
                    bundle.putInt("OFFSET", mBrowserAdapter.count)
                    onConnected(bundle)
                }
            })
            mSwipeRefreshLayout.setOnRefreshListener {
                mBrowserAdapter.clear()
                mBrowserAdapter.notifyDataSetChanged()
                onConnected()
            }
        }

        mMediaFragmentListener.showSearchButton(true)
        updateDescription()

        return rootView
    }

    fun setFilter(filter: CharSequence?) {
        mBrowserAdapter.filter.filter(filter)
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        if (activity is MediaFragmentListener) {
            mMediaFragmentListener = activity
        }
    }

    fun onConnected() {
        mBrowserAdapter.clear()
        onConnected(Bundle())
    }

    fun onConnected(bundle: Bundle) {
        updateTitle()

        // Unsubscribing before subscribing is required if this mediaId already has a subscriber
        // on this MediaBrowser instance. Subscribing to an already subscribed mediaId will replace
        // the callback, but won't trigger the initial callback.onChildrenLoaded.
        //
        // This is temporary: A bug is being fixed that will make subscribe
        // consistently call onChildrenLoaded initially, no matter if it is replacing an existing
        // subscriber or not. Currently this only happens if the mediaID has no previous
        // subscriber or if the media content changes on the service side, so we need to
        // unsubscribe first.
        mMediaFragmentListener.mediaBrowser?.unsubscribe(mMediaId)
        mMediaFragmentListener.mediaBrowser?.subscribe(mMediaId, bundle, mSubscriptionCallback)
    }

    override fun onStart() {
        super.onStart()

        // fetch browsing information to fill the listview:
        val mediaBrowser = mMediaFragmentListener.mediaBrowser

        LogHelper.d(TAG, "fragment.onStart, mediaId=", mMediaId,
                "  onConnected=" + mediaBrowser?.isConnected)

        if (mediaBrowser?.isConnected == true) {
            onConnected()
        }
    }

    override fun onStop() {
        super.onStop()
        val mediaBrowser = mMediaFragmentListener.mediaBrowser
        if (mediaBrowser != null && mediaBrowser.isConnected) {
            mediaBrowser.unsubscribe(mMediaId)
        }
    }

    fun setDescription(description: MediaDescriptionCompat?) {
        mDescription = description
    }

    private fun showNoMedia(show: Boolean) {
        LogHelper.d(TAG, "showNoMedia: $show")
        mNoMediaView.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateTitle() {
        if (MediaIDHelper.MEDIA_ID_ROOT == mMediaId) {
            mMediaFragmentListener.setToolbarTitle(getString(R.string.drawer_allmusic_title))
            return
        } else if (MediaIDHelper.MEDIA_ID_PLUGINS == mMediaId) {
            mMediaFragmentListener.setToolbarTitle(getString(R.string.addons_title))
            return
        }

        val mediaBrowser = mMediaFragmentListener.mediaBrowser
        mediaBrowser?.getItem(mMediaId, object : MediaBrowserCompat.ItemCallback() {
            override fun onItemLoaded(item: MediaBrowserCompat.MediaItem?) {
                mMediaFragmentListener.setToolbarTitle(
                        item?.description?.title)
            }
        })
    }

    private fun updateDescription() {
        activity?.let { activity ->
            mDescription?.let {
                ArtworkHelper.loadArtwork(requests, it, activity.findViewById(R.id.container_image))
                (activity.findViewById<View>(R.id.header_line2) as? TextView)?.text = it.subtitle
                mMediaFragmentListener.enableCollapse(true)
                mMediaFragmentListener.setToolbarTitle(it.title)
        } ?: mMediaFragmentListener.enableCollapse(false) }
    }

    internal interface MediaFragmentListener : MediaBrowserProvider {
        fun onMediaItemSelected(item: MediaBrowserCompat.MediaItem)

        fun setToolbarTitle(title: CharSequence?)

        fun enableCollapse(enable: Boolean)

        fun showSearchButton(show: Boolean)

        fun closeSearchMenu()
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(MediaBrowserFragment::class.java)
        private const val ARG_MEDIA_ID = "media_id"
    }
}
