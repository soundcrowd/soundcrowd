/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.graphics.Color
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v7.widget.RecyclerView
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.sources.MusicProviderSource
import java.util.*

class MediaItemAdapter(private val requests: GlideRequests, private val listener: OnItemClickListener, private val defaultColor: Int) : RecyclerView.Adapter<MediaItemAdapter.ViewHolder>(), Filterable, SectionIndexer {
    private val mLock = Any()

    private var mDataset: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
    // Filter
    private var mFilter: ArrayFilter? = null

    private var mObjects: List<MediaBrowserCompat.MediaItem> = ArrayList()

    // Section
    private var sectionList: List<String> = ArrayList()

    private var positionForSection: List<Int> = ArrayList()

    val isEmpty: Boolean
        get() = mDataset.isEmpty()

    val count: Int
        get() = itemCount

    fun add(item: MediaBrowserCompat.MediaItem) {
        mDataset.add(item)
    }

    fun clear() {
        mDataset.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val convertView = LayoutInflater.from(parent.context)
                .inflate(R.layout.media_list_item, parent, false)
        return ViewHolder(convertView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.mediaItem = mDataset[position]
        val description = holder.mediaItem!!.description
        holder.mTitleView.text = description.title
        holder.mArtistView.text = description.subtitle
        holder.mImageViewSource.setColorFilter(Color.WHITE)

        if (description.extras != null) {
            val duration = description.extras!!.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
            if (duration > 0) {
                holder.mDuration.text = DateUtils.formatElapsedTime(duration / 1000)
            } else {
                holder.mDuration.text = ""
            }
            val source = description.extras?.getString(MusicProviderSource.CUSTOM_METADATA_MEDIA_SOURCE)
            val kind = description.extras?.getString(MusicProviderSource.CUSTOM_METADATA_MEDIA_KIND)
            var iconId = R.drawable.ic_artist
            if ("track" == kind && source != null) {
                iconId = R.drawable.audio_file_white
            } else if ("playlist" == kind) {
                iconId = R.drawable.ic_playlist_music_black_24dp
            }
            GlideApp.with(holder.mImageViewSource).load(iconId).into(holder.mImageViewSource)
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
        holder.mTitleView.setBackgroundColor(vibrant)
        holder.mArtistView.setBackgroundColor(vibrant)
        holder.mDuration.setBackgroundColor(vibrant)
        holder.mImageViewSource.setBackgroundColor(vibrant)
        holder.mImageViewSource.setColorFilter(text)
        holder.mTitleView.setTextColor(text)
        holder.mArtistView.setTextColor(text)
        holder.mDuration.setTextColor(text)
    }

    override fun getItemCount(): Int {
        return mDataset.size
    }

    fun notifyDataChanged() {
        mObjects = ArrayList<MediaBrowserCompat.MediaItem>(mDataset)
        notifyItemsChanged()
    }

    private fun notifyItemsChanged() {
        sort()
        generateSections(mDataset)
        notifyDataSetChanged()
    }

    private fun sort() {
        mDataset.sortWith(Comparator { o1, o2 ->
            o1.description.extras?.getString("index")?.compareTo(o2.description.extras?.getString("index") ?: "", ignoreCase = true) ?: 0 // .replaceAll("[^A-Z]","")
        })
    }

    @Synchronized
    private fun generateSections(objects: List<MediaBrowserCompat.MediaItem>) {
        val sectionList = ArrayList<String>()
        val positionForSection = ArrayList<Int>()

        var currentIndex: String? = ""
        for ((currentCount, item) in objects.withIndex()) {
            val index = item.description.extras?.getString("index")
            if (index != null && currentIndex != index) {
                currentIndex = index
                sectionList.add(index)
                positionForSection.add(currentCount)
            }
        }

        this.sectionList = sectionList
        this.positionForSection = positionForSection
    }


    override fun getSections(): Array<String> {
        return sectionList.toTypedArray()
    }

    override fun getPositionForSection(section: Int): Int {
        return if (positionForSection.size > section) {
            positionForSection[section]
        } else 0
    }

    override fun getSectionForPosition(position: Int): Int {
        for (i in 0 until positionForSection.size - 1) {
            if (positionForSection[i + 1] > position) {
                return i
            }
        }
        return positionForSection.size - 1
    }

    override fun getFilter(): Filter {
        if (mFilter == null) {
            mFilter = ArrayFilter()
        }
        return mFilter as Filter
    }

    interface OnItemClickListener {
        fun onItemClick(item: MediaBrowserCompat.MediaItem)
    }

    /**
     *
     * An array filter constrains the content of the array adapter with
     * a prefix. Each item that does not start with the supplied prefix
     * is removed from the list.
     */
    private inner class ArrayFilter : Filter() {

        override fun performFiltering(prefix: CharSequence?): Filter.FilterResults {
            val results = Filter.FilterResults()

            if (prefix == null || prefix.isEmpty()) {
                val list: ArrayList<MediaBrowserCompat.MediaItem>
                synchronized(mLock) {
                    list = ArrayList(mObjects)
                }
                results.values = list
                results.count = list.size
            } else {
                val prefixString = prefix.toString().toLowerCase()

                val values: ArrayList<MediaBrowserCompat.MediaItem>
                synchronized(mLock) {
                    values = ArrayList(mObjects)
                }

                val count = values.size
                val newValues = ArrayList<MediaBrowserCompat.MediaItem>()

                for (i in 0 until count) {
                    val value = values[i]
                    var valueText = value.description.title!!.toString().toLowerCase()
                    if (value.description.subtitle != null) {
                        valueText = value.description.subtitle!!.toString().toLowerCase() + " " + valueText
                    }
                    val keywords = prefixString.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    var add = true
                    for (keyword in keywords) {
                        if (!valueText.contains(keyword)) {
                            add = false
                            break
                        }
                    }
                    if (add) {
                        newValues.add(value)
                    }
                }

                results.values = newValues
                results.count = newValues.size
            }

            return results
        }

        override fun publishResults(constraint: CharSequence, results: Filter.FilterResults) {
            mDataset = (results.values as List<MediaBrowserCompat.MediaItem>).toMutableList()
            notifyItemsChanged()
        }
    }

    inner class ViewHolder internal constructor(holder: View) : RecyclerView.ViewHolder(holder) {

        val mImageViewArtwork: ImageView = holder.findViewById(R.id.album_art)
        val mTitleView: TextView = holder.findViewById(R.id.title)
        val mArtistView: TextView = holder.findViewById(R.id.description)
        val mDuration: TextView = holder.findViewById(R.id.duration)
        val mImageViewSource: ImageView = holder.findViewById(R.id.source)
        var mediaItem: MediaBrowserCompat.MediaItem? = null

        init {
            holder.setOnClickListener { listener.onItemClick(mediaItem!!) }
        }

    }
}
