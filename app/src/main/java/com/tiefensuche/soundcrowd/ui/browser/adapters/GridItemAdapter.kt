package com.tiefensuche.soundcrowd.ui.browser.adapters

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.session.SessionCommand
import androidx.recyclerview.widget.RecyclerView
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt.COMMAND_LIKE
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.ARG_NAME
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.ARG_PLAYLIST_ID
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_PLAYLIST_ADD
import com.tiefensuche.soundcrowd.service.PlaybackService.Companion.COMMAND_PLAYLIST_CREATE
import com.tiefensuche.soundcrowd.service.Share
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.PLAYLISTS
import com.tiefensuche.soundcrowd.ui.browser.MediaBrowserFragment.MediaFragmentListener

internal class GridItemAdapter(private val requests: GlideRequests, private val listener: MediaFragmentListener) : MediaItemAdapter<GridItemAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val convertView = LayoutInflater.from(parent.context)
            .inflate(R.layout.media_grid_item, parent, false)
        return ViewHolder(convertView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.mediaItem = position
        val description = mDataset[position].mediaMetadata
        holder.mTitleView.text = description.title
        holder.mArtistView.text = description.artist
        holder.mImageViewSource.setColorFilter(Color.WHITE)

        if (description.extras != null) {
            val duration = description.durationMs ?: 0
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

        ArtworkHelper.loadArtwork(requests, mDataset[position], holder.mImageViewArtwork, object : ArtworkHelper.ColorsListener {
            override fun onColorsReady(colors: IntArray) {
                setColors(holder, colors[0], colors[1])
            }

            override fun onError() {
                setColors(holder, ContextCompat.getColor(holder.mTitleView.context, R.color.colorPrimary), Color.WHITE)
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

    private fun shareMedia(holder: ViewHolder) {
        if (holder.adapterPosition >= 0) {
            val metadata =  mDataset[holder.adapterPosition].mediaMetadata
            metadata.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_URL)?.let { url ->
                Share.shareText(holder.itemView.context, url)
            }
        }
    }

    inner class ViewHolder internal constructor(holder: View) : RecyclerView.ViewHolder(holder) {

        val mBackground: ImageView = holder.findViewById(R.id.background)
        val mImageViewArtwork: ImageView = holder.findViewById(R.id.album_art)
        val mTitleView: TextView = holder.findViewById(R.id.title)
        val mArtistView: TextView = holder.findViewById(R.id.description)
        val mDuration: TextView = holder.findViewById(R.id.duration)
        val mImageViewSource: ImageView = holder.findViewById(R.id.source)
        val mMenu: ImageView = holder.findViewById(R.id.menu)
        var mediaItem = 0

        init {
            // Play on clicking
            holder.setOnClickListener { listener.onMediaItemSelected(mDataset, mediaItem) }

            // Open context menu when clicking menu button
            mMenu.visibility = View.GONE
            if (mDataset[mediaItem].mediaMetadata.isPlayable == true) {
                mMenu.visibility = View.VISIBLE
                mMenu.setOnClickListener {
                    val menu = PopupMenu(holder.context, mMenu)
                    menu.menuInflater.inflate(R.menu.popup_mediaitem, menu.menu)
                    val like = menu.menu.findItem(R.id.like)
                    val rating = mDataset[adapterPosition].mediaMetadata.userRating as? HeartRating
                    like.setVisible(rating != null)
                    if (rating != null) {
                        if (rating.isHeart) {
                            like.setTitle(R.string.unlike)
                            like.setIcon(androidx.media3.session.R.drawable.media3_icon_heart_filled)
                        } else {
                            like.setTitle(R.string.like)
                            like.setIcon(androidx.media3.session.R.drawable.media3_icon_heart_unfilled)
                        }
                    }
                    menu.menu.findItem(R.id.share).setVisible(
                        mDataset[adapterPosition].mediaMetadata.extras?.getString(
                            MediaMetadataCompatExt.METADATA_KEY_URL
                        ) != null
                    )
                    menu.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.like -> {
                                listener.mediaBrowser.sendCustomCommand(SessionCommand(COMMAND_LIKE, Bundle.EMPTY), Bundle().also { it.putString(
                                    MEDIA_ID, mDataset[mediaItem].mediaId) })
                            }
                            R.id.addToQueue -> {
                                listener.mediaBrowser.addMediaItem(mDataset[mediaItem])
                            }
                            R.id.playNext -> {
                                listener.mediaBrowser.addMediaItem(listener.mediaBrowser.currentMediaItemIndex + 1, mDataset[mediaItem])
                            }
                            R.id.addToPlaylist -> {
                                showPlaylistsDialog(holder.context, mDataset[mediaItem])
                            }
                            R.id.share -> {
                                shareMedia(this)
                            }
                        }
                        true
                    }
                    menu.setForceShowIcon(true)
                    menu.show()
                }
            }
        }

        private fun showPlaylistsDialog(context: Context, item: MediaItem) {
            val childrenFuture =
                listener.mediaBrowser.getChildren(PLAYLISTS, 0, Int.MAX_VALUE, null)
            childrenFuture.addListener({
                val children = childrenFuture.get().value!!
                lateinit var text : EditText
                val alert = AlertDialog.Builder(context)
                    .setView(R.layout.menu_playlist)
                    .setTitle(R.string.playlist_add)
                    .setCancelable(false)
                    .setPositiveButton(R.string.playlist_create) { _, _ ->
                        listener.mediaBrowser.sendCustomCommand(
                            SessionCommand(
                                COMMAND_PLAYLIST_CREATE,
                                Bundle.EMPTY
                            ), item, Bundle().apply {
                                putString(
                                    ARG_NAME,
                                    text.text.toString()
                                )
                            })
                    }
                    .setNegativeButton(R.string.playlist_cancel) { _, _ -> }
                    .create()

                alert.show()

                val list = alert.findViewById<ListView>(R.id.list_playlists)
                list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, android.R.id.text1, children.map { it.mediaMetadata.title })
                list.onItemClickListener =
                    AdapterView.OnItemClickListener { _, _, position, _ ->
                        listener.mediaBrowser.sendCustomCommand(
                            SessionCommand(
                                COMMAND_PLAYLIST_ADD,
                                Bundle.EMPTY
                            ),
                            item,
                            Bundle().apply { putString(ARG_PLAYLIST_ID, children[position].mediaId) }
                        )
                        alert.dismiss()
                    }
                text = alert.findViewById(R.id.edittext_newplaylist)

            }, ContextCompat.getMainExecutor(context))
        }
    }
}