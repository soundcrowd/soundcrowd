/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.os.Bundle
import android.text.TextUtils
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
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.images.ArtworkHelper
import com.tiefensuche.soundcrowd.images.GlideApp
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.QUERY
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.CUE_POINTS
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LOCAL
import com.tiefensuche.soundcrowd.ui.browser.GridMediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.MediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.StreamMediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.intro.ShowcaseViewManager
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
    internal lateinit var controls: RelativeLayout
    private var collapsingToolbarLayout: CollapsingToolbarLayout? = null
    private var toolbarHeader: View? = null
    private var headerLineTitle: TextView? = null
    private var headerLineSubtitle: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        controls = findViewById(R.id.controls)
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar)
        toolbarHeader = findViewById(R.id.toolbar_header)
        headerLineTitle = findViewById(R.id.header_line1)
        headerLineSubtitle = findViewById(R.id.header_line2)

        val playPauseButton = findViewById<ImageView>(R.id.play_pause)
        val controlsContainer = findViewById<RelativeLayout>(R.id.controls_layout)

        slidingUpPanelLayout.addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) {
                controls.alpha = 1 - slideOffset
            }

            override fun onPanelStateChanged(panel: View, previousState: SlidingUpPanelLayout.PanelState, newState: SlidingUpPanelLayout.PanelState) {
                mPanelState = newState
                playPauseButton.isClickable = newState != SlidingUpPanelLayout.PanelState.EXPANDED
                for (i in 0 until controlsContainer.childCount) {
                    controlsContainer.getChildAt(i).isClickable = newState == SlidingUpPanelLayout.PanelState.EXPANDED
                }
                if (newState == SlidingUpPanelLayout.PanelState.EXPANDED && findViewById<View>(R.id.waveformView).visibility == View.VISIBLE) {
                    ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.WAVEFORM_SEEKING, this@MusicPlayerActivity)
                    ShowcaseViewManager.introduce(ShowcaseViewManager.ShowcaseFunction.CUE_POINT, this@MusicPlayerActivity)
                }
            }
        })

        if (mPanelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            controls.alpha = 0f
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        searchItem = mToolbar?.menu?.findItem(R.id.action_search)

        // Inflate the menu; this adds items to the action bar if it is present.
        MenuItemCompat.setOnActionExpandListener(searchItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(menuItem: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(menuItem: MenuItem): Boolean {
                supportFragmentManager.popBackStack()
                return true
            }
        })

        searchView = MenuItemCompat.getActionView(searchItem) as? SearchView
                ?: throw RuntimeException()
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                currentFragmentMediaId?.let {
                    if (it == CUE_POINTS)
                        return false
                    val bundle = Bundle()
                    if (!it.startsWith(LOCAL))
                        bundle.putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name)
                    navigateToBrowser(QUERY + CATEGORY_SEPARATOR + "Tracks" + CATEGORY_SEPARATOR + MediaIDHelper.toBrowsableName(query))
                    searchView?.clearFocus()
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return false
            }
        })

        return true
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
        setTitle(title)
    }

    override fun setSubtitle(title: CharSequence?) {
        headerLineSubtitle?.text = title
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

        if (!TextUtils.equals(currentMediaId, mediaId)) {
            val fragment = if (currentMediaId.startsWith(LOCAL)) GridMediaBrowserFragment() else StreamMediaBrowserFragment()
            val bundle = Bundle()
            if (mediaId != null) {
                var path = currentMediaId
                if (mediaId.contains(QUERY) && path.contains(CATEGORY_SEPARATOR))
                    path = path.substring(0, path.indexOf(CATEGORY_SEPARATOR))
                bundle.putString(MEDIA_ID, path + CATEGORY_SEPARATOR + mediaId)
            }
            fragment.arguments = bundle
            val transaction = supportFragmentManager.beginTransaction().replace(R.id.container, fragment, MediaBrowserFragment::class.java.name)
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            if (mediaId != null) {
                transaction.addToBackStack(null)
            }
            transaction.commit()
        }
    }

    override fun showSearchButton(show: Boolean) {
        searchItem?.isVisible = show
        searchView?.visibility = if (show) View.VISIBLE else View.GONE
    }

    companion object {

        const val EXTRA_START_FULLSCREEN = "com.tiefensuche.soundcrowd.EXTRA_START_FULLSCREEN"

        /**
         * Optionally used with [.EXTRA_START_FULLSCREEN] to carry a MediaDescription to
         * the [FullScreenPlayerFragment], speeding up the screen rendering
         * while the [android.support.v4.media.session.MediaControllerCompat] is connecting.
         */
        private val TAG = MusicPlayerActivity::class.simpleName
        private var mPanelState: SlidingUpPanelLayout.PanelState = SlidingUpPanelLayout.PanelState.COLLAPSED
    }
}