package com.tiefensuche.soundcrowd.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.session.SessionCommand
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.ARG_PLAYLIST_ID
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.ARG_POSITION
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_PLAYLIST_MOVE
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_PLAYLIST_REMOVE
import com.tiefensuche.soundcrowd.ui.browser.adapters.ListItemAdapter

internal class PlaylistMediaBrowserFragment : MediaBrowserFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBrowserAdapter = ListItemAdapter(requests, mMediaFragmentListener)
    }

    override fun requestMedia(offset: Int, refresh: Boolean) {
        super.requestMedia(offset, true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)
        mRecyclerView.adapter = mBrowserAdapter
        mRecyclerView.layoutManager = LinearLayoutManager(rootView.context)
        ItemTouchHelper(ItemCallback()).attachToRecyclerView(mRecyclerView)
        return rootView
    }

    inner class ItemCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START or ItemTouchHelper.END) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.adapterPosition
            val toPosition = target.adapterPosition
            mMediaFragmentListener.mediaBrowser.sendCustomCommand(SessionCommand(COMMAND_PLAYLIST_MOVE, Bundle.EMPTY), mBrowserAdapter.mDataset[viewHolder.adapterPosition], Bundle().apply {
                putString(ARG_PLAYLIST_ID, mediaId.substring(mediaId.indexOf('/')+1))
                putInt(ARG_POSITION, toPosition)
            })
            mBrowserAdapter.mDataset.add(toPosition, mBrowserAdapter.mDataset.removeAt(fromPosition))
            recyclerView.adapter!!.notifyItemMoved(fromPosition, toPosition)
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            mMediaFragmentListener.mediaBrowser.sendCustomCommand(SessionCommand(COMMAND_PLAYLIST_REMOVE, Bundle.EMPTY), mBrowserAdapter.mDataset[viewHolder.adapterPosition], Bundle().apply {
                putString(ARG_PLAYLIST_ID, mediaId.substring(mediaId.indexOf('/')+1))
            })
            mBrowserAdapter.remove(viewHolder.adapterPosition)
        }

    }
}