/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.media3.common.MediaItem
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.QUERY
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.SUGGESTIONS
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.CUE_POINTS
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LOCAL
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.PLAYLISTS
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.NAME
import com.tiefensuche.soundcrowd.ui.browser.CueMediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.GridMediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.MediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.PlaylistMediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.StreamMediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.SuggestionMediaBrowserFragment
import com.tiefensuche.soundcrowd.utils.MediaIDHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.CATEGORY_SEPARATOR

/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
internal class MusicPlayerActivity : BaseActivity(), MediaBrowserFragment.MediaFragmentListener {

    private var searchView: SearchView? = null
    private var searchItem: MenuItem? = null
    private var collapsingToolbarLayout: CollapsingToolbarLayout? = null
    private var toolbarHeader: View? = null
    private var headerLineTitle: TextView? = null
    private var headerLineSubtitle: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var searchSubmitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar)
        toolbarHeader = findViewById(R.id.toolbar_header)
        headerLineTitle = findViewById(R.id.header_line1)
        headerLineSubtitle = findViewById(R.id.header_line2)

        supportFragmentManager.addFragmentOnAttachListener { _, _ ->
            val controls = findViewById<RelativeLayout>(R.id.controls)
            val playPauseButton = findViewById<ImageView>(R.id.play_pause)
            val controlsContainer = findViewById<RelativeLayout>(R.id.controls_layout)

            sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    playPauseButton.isClickable = newState != BottomSheetBehavior.STATE_EXPANDED
                    for (i in 0 until controlsContainer.childCount) {
                        controlsContainer.getChildAt(i).isClickable = newState == BottomSheetBehavior.STATE_EXPANDED
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    controls.alpha = 1 - slideOffset
                }

            })

            if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                controls.alpha = 0f
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        searchItem = mToolbar?.menu?.findItem(R.id.action_search)

        // Inflate the menu; this adds items to the action bar if it is present.
        MenuItemCompat.setOnActionExpandListener(searchItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                menu.setGroupVisible(1, true)
                return true
            }

            override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                menu.setGroupVisible(1, false)
                if (!searchSubmitted)
                    supportFragmentManager.popBackStackImmediate()
                searchSubmitted = false
                return true
            }
        })

        searchView = MenuItemCompat.getActionView(searchItem) as? SearchView
                ?: throw RuntimeException()
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                supportFragmentManager.popBackStack()
                navigateToBrowser(QUERY + CATEGORY_SEPARATOR + selectedSearchCategory + CATEGORY_SEPARATOR + MediaIDHelper.toBrowsableName(query))
                searchSubmitted = true
                searchItem?.collapseActionView()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({
                    suggestionFragment?.setQuery(newText)
                }, 1000)
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_search) {
            navigateToBrowser("SUGGESTIONS$CATEGORY_SEPARATOR$selectedSearchCategory")
            return true
        }
        if (item.itemId == android.R.id.home && supportFragmentManager.backStackEntryCount > 1 && supportFragmentManager.getBackStackEntryAt(supportFragmentManager.backStackEntryCount - 2).name == "SUGGESTIONS")
                supportFragmentManager.popBackStackImmediate(null, 0)
        return super.onOptionsItemSelected(item)
    }

    override fun onMediaItemSelected(items: List<MediaItem>, index: Int, position: Long) {
        val item = items[index]
        if (item.mediaMetadata.isPlayable == true) {
            mediaBrowser.setMediaItems(items, index, position)
            mediaBrowser.prepare()
            mediaBrowser.play()
        } else if (item.mediaMetadata.isBrowsable == true) {
            Log.d(TAG, "isBrowsable: ${item.mediaId}")
            navigateToBrowser(item.mediaId)
        } else {
            Log.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: mediaId=${item.mediaId}")
        }
    }

    override fun setToolbarTitle(title: CharSequence?) {
        collapsingToolbarLayout?.title = title
        headerLineTitle?.text = title
        headerLineTitle?.visibility = if (title?.isNotEmpty() == true) View.VISIBLE else View.GONE
        setTitle(title)
    }

    override fun setSubtitle(title: CharSequence?) {
        headerLineSubtitle?.text = title
        headerLineSubtitle?.visibility = if (title?.isNotEmpty() == true) View.VISIBLE else View.GONE
    }

    override fun setBackground(description: MediaItem) {
        ArtworkHelper.loadArtwork(GlideApp.with(this), description, findViewById(R.id.container_image))
    }

    override fun enableCollapse(enable: Boolean) {
        toolbarHeader?.let {
            if (it.visibility == View.VISIBLE && !enable || it.visibility == View.GONE && enable) {
                it.visibility = if (enable) View.VISIBLE else View.GONE
                collapsingToolbarLayout?.isTitleEnabled = enable
            }
        }
    }

    private fun navigateToBrowser(mediaId: String?) {
        Log.d(TAG, "navigateToBrowser, mediaId=$mediaId")
        val currentMediaId = currentFragmentMediaId ?: return

            val fragment =
                if (mediaId?.startsWith(SUGGESTIONS) == true)
                    SuggestionMediaBrowserFragment()
                else if (currentMediaId.startsWith(LOCAL))
                    GridMediaBrowserFragment()
                else if (currentMediaId.startsWith(PLAYLISTS))
                    PlaylistMediaBrowserFragment()
                else if (currentMediaId.startsWith(CUE_POINTS))
                    CueMediaBrowserFragment()
                else
                    StreamMediaBrowserFragment()
            val bundle = Bundle()
            if (mediaId != null) {
                var path = currentMediaId
                if ((mediaId.contains(QUERY) || mediaId.contains(SUGGESTIONS)) && path.contains(CATEGORY_SEPARATOR))
                    path = path.substringBefore(CATEGORY_SEPARATOR)
                bundle.putString(MEDIA_ID, path + CATEGORY_SEPARATOR + mediaId)
                if (mediaId.contains(QUERY))
                    bundle.putString(NAME, mediaId.substringAfterLast(CATEGORY_SEPARATOR))
            }
            fragment.arguments = bundle
            val transaction = supportFragmentManager.beginTransaction().replace(R.id.container, fragment, if (mediaId?.contains(
                    SUGGESTIONS) == true) SUGGESTIONS else MediaBrowserFragment::class.java.name)
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            if (mediaId != null && !currentMediaId.contains(SUGGESTIONS)) {
                transaction.addToBackStack(MediaBrowserFragment::class.java.name)
            }
            transaction.commit()
    }

    override fun showSearchButton(show: Boolean) {
        searchItem?.isVisible = show
        searchView?.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun setQuery(query: CharSequence?, submit: Boolean) {
        searchView?.setQuery(query, submit)
    }

    companion object {

        const val EXTRA_START_FULLSCREEN = "com.tiefensuche.soundcrowd.EXTRA_START_FULLSCREEN"

        /**
         * Optionally used with [.EXTRA_START_FULLSCREEN] to carry a MediaDescription to
         * the [FullScreenPlayerFragment], speeding up the screen rendering
         * while the [android.support.v4.media.session.MediaControllerCompat] is connecting.
         */
        private val TAG = MusicPlayerActivity::class.simpleName
    }
}