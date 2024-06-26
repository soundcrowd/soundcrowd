/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.support.v4.media.MediaBrowserCompat
import android.widget.Filter
import android.widget.Filterable
import android.widget.SectionIndexer
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

internal abstract class MediaItemAdapter<T : RecyclerView.ViewHolder?> : RecyclerView.Adapter<T>(), Filterable, SectionIndexer {

    private val mLock = Any()
    internal var mDataset: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()

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

    override fun getItemCount(): Int {
        return mDataset.size
    }

    fun notifyDataChanged() {
        mObjects = ArrayList(mDataset)
        notifyDataSetChanged()
    }

    fun generateSections() {
        val sectionList = ArrayList<Char>()
        val positionForSection = ArrayList<Int>()

        var currentIndex = '#'
        for ((currentCount, item) in mDataset.withIndex()) {
            val index = item.description.title?.first()?.uppercaseChar() ?: '#'
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

        override fun performFiltering(prefix: CharSequence?): FilterResults {
            val results = FilterResults()

            if (prefix == null || prefix.isEmpty()) {
                val list: ArrayList<MediaBrowserCompat.MediaItem>
                synchronized(mLock) {
                    list = ArrayList(mObjects)
                }
                results.values = list
                results.count = list.size
            } else {
                val prefixString = prefix.toString().lowercase(Locale.getDefault())

                val values: ArrayList<MediaBrowserCompat.MediaItem>
                synchronized(mLock) {
                    values = ArrayList(mObjects)
                }

                val count = values.size
                val newValues = ArrayList<MediaBrowserCompat.MediaItem>()

                for (i in 0 until count) {
                    val value = values[i]
                    var valueText = value.description.title?.toString()
                        ?.lowercase(Locale.getDefault())
                    if (value.description.subtitle != null) {
                        valueText = value.description.subtitle?.toString()
                            ?.lowercase(Locale.getDefault()) + " " + valueText
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

        override fun publishResults(constraint: CharSequence, results: FilterResults) {
            mDataset = (results.values as List<MediaBrowserCompat.MediaItem>).toMutableList()
            notifyDataSetChanged()
        }
    }
}