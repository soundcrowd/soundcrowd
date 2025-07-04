package com.tiefensuche.soundcrowd.ui.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.session.SessionCommand
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_PLAYLIST_DELETE
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.PLAYLISTS

internal class PlaylistsMediaBrowserFragment : SortedListMediaBrowserFragment() {

    override val mediaId = PLAYLISTS

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val rootView = super.onCreateView(inflater, container, savedInstanceState)
        ItemTouchHelper(ItemCallback()).attachToRecyclerView(mRecyclerView)
        return rootView
    }

    override fun updateDescription() {
        mMediaFragmentListener.setToolbarTitle(getString(R.string.drawer_playlists_title))
        mMediaFragmentListener.enableCollapse(false)
    }

    inner class ItemCallback : ItemTouchHelper.SimpleCallback(
        0, ItemTouchHelper.START or ItemTouchHelper.END) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            mMediaFragmentListener.mediaBrowser.sendCustomCommand(
                SessionCommand(
                    COMMAND_PLAYLIST_DELETE, Bundle.EMPTY
                ), mBrowserAdapter.mDataset[viewHolder.adapterPosition], Bundle()
            )
            mBrowserAdapter.remove(viewHolder.adapterPosition)
        }

    }
}