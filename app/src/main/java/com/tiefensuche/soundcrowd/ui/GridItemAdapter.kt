package com.tiefensuche.soundcrowd.ui

import android.content.Context
import android.graphics.Color
import android.support.v4.media.MediaMetadataCompat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests

internal class GridItemAdapter(private val requests: GlideRequests, private val listener: OnItemClickListener, private val defaultColor: Int) : MediaItemAdapter<GridItemAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val convertView = LayoutInflater.from(parent.context)
            .inflate(R.layout.media_grid_item, parent, false)
        return ViewHolder(convertView, parent.context)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.mediaItem = position
        val description = mDataset[position].description
        holder.mTitleView.text = description.title
        holder.mArtistView.text = description.subtitle
        holder.mImageViewSource.setColorFilter(Color.WHITE)

        if (description.extras != null) {
            val duration = description.extras?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                ?: 0
            if (duration > 0) {
                holder.mDuration.text = DateUtils.formatElapsedTime(duration / 1000)
            } else {
                holder.mDuration.text = ""
            }

            description.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_TYPE)?.let {
                val iconId = when (MediaMetadataCompatExt.MediaType.valueOf(it)) {
                    MediaMetadataCompatExt.MediaType.MEDIA -> R.drawable.baseline_audiotrack_24
                    MediaMetadataCompatExt.MediaType.COLLECTION -> R.drawable.baseline_library_music_24
                    MediaMetadataCompatExt.MediaType.STREAM -> R.drawable.baseline_view_stream_24
                }
                GlideApp.with(holder.mImageViewSource).load(iconId).into(holder.mImageViewSource)
            }
        }

        ArtworkHelper.loadArtwork(requests, description, holder.mImageViewArtwork, object : ArtworkHelper.ColorsListener {
            override fun onColorsReady(colors: IntArray) {
                setColors(holder, colors[0], colors[1])
            }

            override fun onError() {
                setColors(holder, defaultColor, Color.WHITE)
            }
        })
    }

    private fun setColors(holder: ViewHolder, vibrant: Int, text: Int) {
        holder.mBackground.setBackgroundColor(vibrant)
        holder.mImageViewSource.setColorFilter(text)
        holder.mTitleView.setTextColor(text)
        holder.mArtistView.setTextColor(text)
        holder.mDuration.setTextColor(text)
    }

    inner class ViewHolder internal constructor(holder: View, parentContext: Context) : RecyclerView.ViewHolder(holder) {

        val mBackground: ImageView = holder.findViewById(R.id.background)
        val mImageViewArtwork: ImageView = holder.findViewById(R.id.album_art)
        val mTitleView: TextView = holder.findViewById(R.id.title)
        val mArtistView: TextView = holder.findViewById(R.id.description)
        val mDuration: TextView = holder.findViewById(R.id.duration)
        val mImageViewSource: ImageView = holder.findViewById(R.id.source)
        var mediaItem = 0

        init {
            holder.setOnClickListener { listener.onItemClick(mDataset[mediaItem]) }
            holder.setOnLongClickListener {
                MaterialAlertDialogBuilder(parentContext)
                    .setTitle("test")
                    .setMessage("copied")
                    .show()
                return@setOnLongClickListener true
            }
        }
    }
}