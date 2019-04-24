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
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.images.GlideRequests
import com.tiefensuche.soundcrowd.utils.Utils
import java.util.*

internal class MediaItemAdapter(private val requests: GlideRequests, private val listener: OnItemClickListener, private val defaultColor: Int) : RecyclerView.Adapter<MediaItemAdapter.ViewHolder>(), Filterable, SectionIndexer {
    private val mLock = Any()

    private var mDataset: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
    // Filter
    private var mFilter: ArrayFilter? = null

    private var mObjects: List<MediaBrowserCompat.MediaItem> = ArrayList()

    // Section
    private var sectionList: List<Char> = ArrayList()

    private var positionForSection: List<Int> = ArrayList()

    internal val isEmpty: Boolean
        get() = mDataset.isEmpty()

    internal val count: Int
        get() = itemCount

    internal fun add(item: MediaBrowserCompat.MediaItem) {
        mDataset.add(item)
    }

    internal fun clear() {
        mDataset.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val convertView = LayoutInflater.from(parent.context)
                .inflate(R.layout.media_list_item, parent, false)
        return ViewHolder(convertView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val mediaItem = mDataset[position]
        holder.mediaItem = mediaItem
        val description = mediaItem.description
        holder.mTitleView.text = description.title
        holder.mArtistView.text = description.subtitle
        holder.mImageViewSource.setColorFilter(Color.WHITE)

        if (description.extras != null) {
            val duration = description.extras?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0
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

    internal fun notifyDataChanged() {
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
            Utils.getIndexCharacter(o1.description.title).compareTo(Utils.getIndexCharacter(o2.description.title))
        })
    }

    @Synchronized
    private fun generateSections(objects: List<MediaBrowserCompat.MediaItem>) {
        val sectionList = ArrayList<Char>()
        val positionForSection = ArrayList<Int>()

        var currentIndex = 'a'
        for ((currentCount, item) in objects.withIndex()) {
            val index = Utils.getIndexCharacter(item.description.title)
            if (currentIndex != index) {
                currentIndex = index
                sectionList.add(index)
                positionForSection.add(currentCount)
            }
        }

        this.sectionList = sectionList
        this.positionForSection = positionForSection
    }


    override fun getSections(): Array<String> {
        return sectionList.map { it.toString() }.toTypedArray()
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
        var filter = mFilter
        if (filter == null) {
            filter = ArrayFilter()
            mFilter = filter
        }
        return filter
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
                    var valueText = value.description.title?.toString()?.toLowerCase()
                    if (value.description.subtitle != null) {
                        valueText = value.description.subtitle?.toString()?.toLowerCase() + " " + valueText
                    }
                    val keywords = prefixString.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    var add = true
                    for (keyword in keywords) {
                        if ((valueText?.contains(keyword)) == false) {
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

    internal inner class ViewHolder internal constructor(holder: View) : RecyclerView.ViewHolder(holder) {

        val mImageViewArtwork: ImageView = holder.findViewById(R.id.album_art)
        val mTitleView: TextView = holder.findViewById(R.id.title)
        val mArtistView: TextView = holder.findViewById(R.id.description)
        val mDuration: TextView = holder.findViewById(R.id.duration)
        val mImageViewSource: ImageView = holder.findViewById(R.id.source)
        var mediaItem: MediaBrowserCompat.MediaItem? = null

        init {
            holder.setOnClickListener { mediaItem?.let { listener.onItemClick(it) } }
        }

    }
}
