/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.SessionError.ERROR_INVALID_STATE
import androidx.media3.session.SessionResult
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.ARG_ERROR
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LOCAL
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.NAME
import com.tiefensuche.soundcrowd.ui.MediaBrowserProvider
import com.tiefensuche.soundcrowd.ui.browser.adapters.MediaItemAdapter
import `in`.myinnos.alphabetsindexfastscrollrecycler.IndexFastScrollRecyclerView

/**
 * A Fragment that lists all the various browsable queues available
 * from a [androidx.media3.session.MediaBrowser].
 *
 * Once connected, the fragment requests to get all the children.
 * All [MediaItem]'s that can be browsed are shown in a ListView.
 */
internal abstract class MediaBrowserFragment : Fragment() {

    lateinit var mBrowserAdapter: MediaItemAdapter<*>
    lateinit var mMediaFragmentListener: MediaFragmentListener
    private lateinit var mNoMediaView: TextView
    lateinit var mProgressBar: ProgressBar
    lateinit var mRecyclerView: IndexFastScrollRecyclerView
    lateinit var requests: GlideRequests

    open fun onResult(items: List<MediaItem>) {
        Log.d(TAG, "fragment onResult, count=${items.count()}")

        // hide loading indicators
        mProgressBar.visibility = View.GONE

        mBrowserAdapter.mDataset.addAll(items)
        mBrowserAdapter.notifyDataChanged()
        mProgressBar.visibility = View.GONE
        showNoMedia(mBrowserAdapter.isEmpty)
    }

    open fun onError(message: String) {
        Log.e(TAG, "browse fragment onError, id=$id")

        // hide loading indicators
        mProgressBar.visibility = View.GONE
        showMessage(message)
    }

    internal open val mediaId: String
        get() = arguments?.getString(MEDIA_ID) ?: DEFAULT_MEDIA_ID

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mMediaFragmentListener = activity as MediaFragmentListener
    }

    open fun requestMedia(offset: Int = 0, refresh: Boolean = false) {
        mProgressBar.visibility = View.VISIBLE

        val bundle = Bundle()
        bundle.putString(MEDIA_ID, mediaId)
        if (refresh)
            bundle.putBoolean(MusicProvider.OPTION_REFRESH, true)

        val childrenFuture =
            mMediaFragmentListener.mediaBrowser.getChildren(mediaId, offset, Int.MAX_VALUE, LibraryParams.Builder().setExtras(bundle).build())
        childrenFuture.addListener({
            val result = childrenFuture.get()
            if (result.resultCode == ERROR_INVALID_STATE)
                result.params?.extras?.getString(ARG_ERROR)?.let { onError(it) }
            else
                result.value?.let { onResult(it) }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onStart() {
        super.onStart()
        if (mMediaFragmentListener.connected && mBrowserAdapter.isEmpty)
            requestMedia()
    }

    private fun showMessage(text: String) {
        mNoMediaView.text = text
        showNoMedia(true)
    }

    private fun showNoMedia(show: Boolean) {
        mNoMediaView.visibility = if (show) View.VISIBLE else View.GONE
    }

    open fun updateDescription() {
        if (!mMediaFragmentListener.connected)
            return
        val childrenFuture =
            mMediaFragmentListener.mediaBrowser.getItem(mediaId)
        childrenFuture.addListener({
            if (childrenFuture.get().resultCode == SessionResult.RESULT_SUCCESS) {
                val children = childrenFuture.get().value!!
                mMediaFragmentListener.enableCollapse(true)
                mMediaFragmentListener.setToolbarTitle(children.mediaMetadata.title)
                mMediaFragmentListener.setSubtitle(children.mediaMetadata.artist)
                mMediaFragmentListener.setBackground(children)
            } else {
                mMediaFragmentListener.setToolbarTitle(arguments?.getString(NAME))
                mMediaFragmentListener.enableCollapse(false)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    internal interface MediaFragmentListener : MediaBrowserProvider {
        fun onMediaItemSelected(items: List<MediaItem>, index: Int, position: Long = 0)
        fun setToolbarTitle(title: CharSequence?)
        fun setSubtitle(title: CharSequence?)
        fun setBackground(description: MediaItem)
        fun enableCollapse(enable: Boolean)
        fun showSearchButton(show: Boolean)
        fun setQuery(query: CharSequence?, submit: Boolean)
    }

    companion object {
        private val TAG = MediaBrowserFragment::class.simpleName
        internal const val DEFAULT_MEDIA_ID = LOCAL
    }
}