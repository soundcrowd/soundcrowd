package com.tiefensuche.soundcrowd.ui.browser.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt.FROM_RECENT_SEARCH_QUERIES

internal class SuggestionItemAdapter(private val listener: OnItemClickListener) :
    MediaItemAdapter<SuggestionItemAdapter.ViewHolder>() {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_search_suggestion, viewGroup, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.text.text = mDataset[position].mediaMetadata.title
        viewHolder.icon.setImageResource(
            if (mDataset[position].mediaMetadata.extras?.getBoolean(FROM_RECENT_SEARCH_QUERIES) == true)
                R.drawable.history_24px
            else
                R.drawable.ic_round_search_24
        )
    }

    interface OnItemClickListener {
        fun onItemClick(item: MediaItem)
        fun onItemInsert(item: MediaItem)
    }

    inner class ViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.item_suggestion_icon)
        val text: TextView = view.findViewById(R.id.item_suggestion_query)
        private val insert: View = view.findViewById(R.id.suggestion_insert)

        init {
            text.setOnClickListener { listener.onItemClick(mDataset[adapterPosition]) }
            insert.setOnClickListener { listener.onItemInsert(mDataset[adapterPosition]) }
        }
    }
}