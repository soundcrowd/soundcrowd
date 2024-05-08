package com.tiefensuche.soundcrowd.ui

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.CATEGORY
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.NAME
import com.tiefensuche.soundcrowd.ui.MediaBrowserFragment.Companion.DEFAULT_MEDIA_ID
import com.tiefensuche.soundcrowd.utils.MediaIDHelper

internal open class TabFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = arguments?.getString(NAME)
        val list = arguments?.getParcelableArrayList<Bundle>(CATEGORY)!!
        val tabLayout: TabLayout = view.findViewById(R.id.tab_layout)

        for (item in list) {
            tabLayout.addTab(tabLayout.newTab().setText(item.getString(CATEGORY)))
        }

        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectId(list[tab.position].getString(NAME)!!, title)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        selectId(list[0].getString(NAME)!!, title)
    }

    fun selectId(mediaId: String, title: String?) {
        setFragment(StreamMediaBrowserFragment(), mediaId, title)
    }

    internal fun setFragment(fragment: MediaBrowserFragment, mediaId: String, title: String?) {
        activity?.let {
            fragment.arguments = Bundle().apply {
                putString(MEDIA_ID, mediaId)
                putString(NAME, title)
            }
            it.supportFragmentManager.beginTransaction()
                .replace(R.id.tab_container, fragment, MediaBrowserFragment::class.java.name).commit()
        }
    }
}

internal class LocalTabFragment : TabFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tabLayout: TabLayout = view.findViewById(R.id.tab_layout)

        for (item in listOf("Tracks", "Artists", "Albums")) {
            tabLayout.addTab(tabLayout.newTab().setText(item))
        }

        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectId(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}

        })

        selectId(0)
    }

    fun selectId(id: Int) {
        setFragment(
            when (id) {
                0 -> GridMediaBrowserFragment()
                else -> ListMediaBrowserFragment()
            }, when (id) {
                0 -> MusicProvider.Media.LOCAL
                1 -> MusicProvider.Media.LOCAL + MediaIDHelper.CATEGORY_SEPARATOR + MediaMetadataCompat.METADATA_KEY_ARTIST
                2 -> MusicProvider.Media.LOCAL + MediaIDHelper.CATEGORY_SEPARATOR + MediaMetadataCompat.METADATA_KEY_ALBUM
                else -> DEFAULT_MEDIA_ID
            }, getString(R.string.drawer_allmusic_title))
    }
}