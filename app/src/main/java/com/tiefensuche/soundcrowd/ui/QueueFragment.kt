package com.tiefensuche.soundcrowd.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.ui.browser.MediaBrowserFragment.MediaFragmentListener
import com.tiefensuche.soundcrowd.ui.browser.adapters.ListItemAdapter
import com.tiefensuche.soundcrowd.ui.browser.adapters.MediaItemAdapter
import `in`.myinnos.alphabetsindexfastscrollrecycler.IndexFastScrollRecyclerView

internal class QueueFragment : Fragment() {

    lateinit var mMediaFragmentListener: MediaFragmentListener
    lateinit var mBrowserAdapter: MediaItemAdapter<*>
    lateinit var requests: GlideRequests

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is MediaFragmentListener)
            mMediaFragmentListener = context
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requests = GlideApp.with(this)
        mBrowserAdapter = ListItemAdapter(requests, mMediaFragmentListener)

        for (i in 0 until mMediaFragmentListener.mediaBrowser.mediaItemCount) {
            mBrowserAdapter.add(mMediaFragmentListener.mediaBrowser.getMediaItemAt(i))
        }
        mBrowserAdapter.notifyDataChanged()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = inflater.inflate(R.layout.fragment_list, container, false)
        val mRecyclerView = rootView.findViewById<IndexFastScrollRecyclerView>(R.id.list_view)
        mRecyclerView.adapter = mBrowserAdapter
        mRecyclerView.layoutManager = LinearLayoutManager(rootView.context)
        mRecyclerView.setIndexBarVisibility(false)
        ItemTouchHelper(ItemCallback()).attachToRecyclerView(mRecyclerView)
        mMediaFragmentListener.setToolbarTitle(getString(R.string.drawer_playing_queue_title))
        mMediaFragmentListener.enableCollapse(false)
        mMediaFragmentListener.showSearchButton(false)
        return rootView
    }

    inner class ItemCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START or ItemTouchHelper.END) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.adapterPosition
            val toPosition = target.adapterPosition
            mMediaFragmentListener.mediaBrowser.moveMediaItem(fromPosition, toPosition)
            recyclerView.adapter!!.notifyItemMoved(fromPosition, toPosition)
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            mMediaFragmentListener.mediaBrowser.removeMediaItem(viewHolder.adapterPosition)
            mBrowserAdapter.remove(viewHolder.adapterPosition)
        }

    }
}