package com.tiefensuche.soundcrowd.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideRequests

internal class ListItemAdapter(private val requests: GlideRequests, private val listener: OnItemClickListener) : MediaItemAdapter<ListItemAdapter.ViewHolder>() {

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.media_list_item, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.mediaItem = position

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        viewHolder.title.text = mDataset[position].description.title
        viewHolder.artist.text = mDataset[position].description.subtitle

        ArtworkHelper.loadArtwork(requests, mDataset[position].description, viewHolder.mImageViewArtwork)
    }

    inner class ViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {

        var mediaItem = 0
        val title: TextView = view.findViewById(R.id.title)
        val artist: TextView = view.findViewById(R.id.artist)
        val mImageViewArtwork: ImageView = view.findViewById(R.id.album_art)

        init {
            view.setOnClickListener { listener.onItemClick(mDataset[mediaItem]) }
        }
    }
}