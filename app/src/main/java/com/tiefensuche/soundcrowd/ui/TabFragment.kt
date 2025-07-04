package com.tiefensuche.soundcrowd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.sources.MusicProvider
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.CATEGORY
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.NAME
import com.tiefensuche.soundcrowd.ui.browser.GridMediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.SortedListMediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.StreamMediaBrowserFragment
import com.tiefensuche.soundcrowd.utils.MediaIDHelper

internal abstract class TabFragment : Fragment() {

    internal var title: String? = null
    internal lateinit var mediaIds : List<String>
    internal lateinit var categories : List<String>
    private lateinit var viewPager: ViewPager2

    internal open val mediaId: String
        get() = mediaIds[viewPager.currentItem]

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val tabLayout: TabLayout = view.findViewById(R.id.tab_layout)

        viewPager = view.findViewById(R.id.pager)
        // Disable swiping with
//        viewPager.isUserInputEnabled = false
        viewPager.adapter = object : FragmentStateAdapter(this) {

            override fun getItemCount(): Int {
                return mediaIds.size
            }

            override fun createFragment(position: Int): Fragment {
                val fragment = this@TabFragment.createFragment(position)
                fragment.arguments = Bundle().apply {
                    putString(MEDIA_ID, mediaIds[position])
                    putString(NAME, title)
                }
                return fragment
            }

        }

        // For transitioning between categories via swiping, smooth scrolling should be used.
        // When selecting a tab directly, smooth scrolling should not be used to avoid cycling
        // through all categories and causing all initializations and loads.
        // The described functionality is not possible with TabLayoutMediator smooth scrolling can
        // either be used for both or not
//        TabLayoutMediator(tabLayout, viewPager, false, true) { tab, position ->
//            tab.text = categories[position]
//        }.attach()

        // The following workaround provides the intended functionality.

        for (item in categories) {
            tabLayout.addTab(tabLayout.newTab().setText(item))
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                tabLayout.getTabAt(position)?.select()
            }
        })

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position != viewPager.currentItem)
                    viewPager.setCurrentItem(tab.position, false)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    abstract fun createFragment(position: Int): Fragment
}

internal class LocalTabFragment : TabFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        title = getString(R.string.drawer_allmusic_title)
        mediaIds = listOf(
            MusicProvider.Media.LOCAL,
            MusicProvider.Media.LOCAL + MediaIDHelper.CATEGORY_SEPARATOR + "METADATA_KEY_ARTIST",
            MusicProvider.Media.LOCAL + MediaIDHelper.CATEGORY_SEPARATOR + "METADATA_KEY_ALBUM"
        )
        categories = listOf("Tracks", "Artists", "Albums")
        super.onViewCreated(view, savedInstanceState)
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GridMediaBrowserFragment()
            else -> SortedListMediaBrowserFragment()
        }
    }
}

internal class PluginTabFragment : TabFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        title = arguments?.getString(NAME)!!
        val args = arguments?.getParcelableArrayList<Bundle>(CATEGORY)!!
        mediaIds = args.map { it.getString(NAME)!! }
        categories = args.map { it.getString(CATEGORY)!! }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun createFragment(position: Int): Fragment {
        return StreamMediaBrowserFragment()
    }
}