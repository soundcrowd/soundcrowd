/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui.browser.adapters

import android.widget.Filter
import android.widget.Filterable
import android.widget.SectionIndexer
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

internal abstract class MediaItemAdapter<T : RecyclerView.ViewHolder?> : RecyclerView.Adapter<T>(), Filterable, SectionIndexer {

    private val mLock = Any()
    internal var mDataset: MutableList<MediaItem> = ArrayList()

    // Filter
    private var mFilter: ArrayFilter? = null
    private var mObjects: List<MediaItem> = ArrayList()

    // Section
    private var sectionList: List<Char> = ArrayList()
    private var positionForSection: List<Int> = ArrayList()

    internal val isEmpty: Boolean
        get() = mDataset.isEmpty()

    internal val count: Int
        get() = itemCount

    internal fun add(item: MediaItem) {
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
            val index = item.mediaMetadata.title?.first()?.uppercaseChar() ?: '#'
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
        fun onItemClick(items: List<MediaItem>, position: Int)
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
                val list: ArrayList<MediaItem>
                synchronized(mLock) {
                    list = ArrayList(mObjects)
                }
                results.values = list
                results.count = list.size
            } else {
                val prefixString = prefix.toString().lowercase(Locale.getDefault())

                val values: ArrayList<MediaItem>
                synchronized(mLock) {
                    values = ArrayList(mObjects)
                }

                val count = values.size
                val newValues = ArrayList<MediaItem>()

                for (i in 0 until count) {
                    val value = values[i]
                    var valueText = value.mediaMetadata.title?.toString()
                        ?.lowercase(Locale.getDefault())
                    if (value.mediaMetadata.artist != null) {
                        valueText = value.mediaMetadata.artist?.toString()
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
            mDataset = (results.values as List<MediaItem>).toMutableList()
            notifyDataSetChanged()
        }
    }
}