package com.tiefensuche.soundcrowd.ui

import android.support.v4.media.MediaBrowserCompat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.sources.MusicProvider
import org.json.JSONArray

internal class CueListItemAdapter(private val requests: GlideRequests, private val listener: OnItemClickListener) : MediaItemAdapter<CueListItemAdapter.ViewHolder>() {
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.media_cue_list_item, viewGroup, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.mediaItem = position
        viewHolder.title.text = mDataset[position].description.title
        viewHolder.artist.text = mDataset[position].description.subtitle
        ArtworkHelper.loadArtwork(requests, mDataset[position].description, viewHolder.mImageViewArtwork)

        viewHolder.subitem.adapter = CuesAdapter(mDataset[position])
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.subitem.visibility = View.GONE
    }

    inner class ViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {

        var mediaItem = 0
        val title: TextView = view.findViewById(R.id.title)
        val artist: TextView = view.findViewById(R.id.artist)
        val mImageViewArtwork: ImageView = view.findViewById(R.id.album_art)
        val subitem: RecyclerView = view.findViewById(R.id.sub_item)

        init {
            subitem.layoutManager = LinearLayoutManager(subitem.context)
            view.setOnClickListener {
                subitem.visibility = if (subitem.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
    }

    interface OnItemClickListener {
        fun onItemClick(item: MediaBrowserCompat.MediaItem, position: Long)
        fun onItemDelete(item: MediaBrowserCompat.MediaItem, position: Long)
    }

    inner class CuesAdapter(val item: MediaBrowserCompat.MediaItem) : RecyclerView.Adapter<CuesViewHolder>() {

        private val cues = ArrayList<Pair<Long, String>>()

        init {
            item.description.extras?.getString(MusicProvider.Cues.CUES)?.let {
                val json = JSONArray(it)
                for (i in 0 until json.length()) {
                    val pos = json.getJSONObject(i).getLong(MusicProvider.Cues.POSITION)
                    val desc = json.getJSONObject(i).getString(MusicProvider.Cues.DESCRIPTION)
                    this.cues.add(Pair(pos, desc))
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CuesViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.text_row_item, parent, false)

            return CuesViewHolder(view)
        }

        override fun getItemCount(): Int {
            return cues.size
        }

        override fun onBindViewHolder(holder: CuesViewHolder, position: Int) {
            val cue = cues[position]
            holder.buttonPlay.text = DateUtils.formatElapsedTime(cue.first / 1000) + if (cue.second != "") ": ${cue.second}" else ""
            holder.buttonPlay.setOnClickListener { listener.onItemClick(item, cue.first) }
            holder.buttonDelete.setOnClickListener {
                listener.onItemDelete(item, cue.first)
                cues.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, itemCount)
            }
        }
    }

    inner class CuesViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {
        val buttonPlay: Button = view.findViewById(R.id.button)
        val buttonDelete: ImageButton = view.findViewById(R.id.delete)
    }
}