/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import com.google.android.material.appbar.CollapsingToolbarLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.core.view.MenuItemCompat
import androidx.appcompat.widget.SearchView
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.QUERY
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.LOCAL
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
    internal lateinit var collapsingToolbarLayout: CollapsingToolbarLayout
    private var toolbarHeader: View? = null
    private lateinit var preferences: SharedPreferences

    private val mediaId: String?
        get() {
            val fragment = browseFragment ?: return null
            return fragment.mediaId
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeFromParams(savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        controls = findViewById(R.id.controls)
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar)
        toolbarHeader = findViewById(R.id.toolbar_header)

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

        checkPermissions()

        if (mPanelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            controls.alpha = 0f
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        searchItem = mToolbar.menu.findItem(R.id.action_search)

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
                browseFragment?.let {
                    val bundle = Bundle()
                    if (mediaId?.startsWith(LOCAL) != true)
                        bundle.putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name)
                    navigateToBrowser(QUERY + CATEGORY_SEPARATOR + MediaIDHelper.toBrowsableName(query),
                            MediaDescriptionCompat.Builder()
                                    .setTitle(query)
                                    .setSubtitle(getString(R.string.search_title))
                                    .setExtras(bundle).build())
                    searchView?.clearFocus()
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                val fragment = supportFragmentManager.findFragmentById(R.id.container)
                if (fragment is MediaBrowserFragment) {
                    fragment.setFilter(newText)
                } else if (fragment is CueListFragment) {
                    fragment.setFilter(newText)
                }
                return true
            }
        })

        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (mediaId != null) {
            outState.putString(SAVED_MEDIA_ID, mediaId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onMediaItemSelected(item: MediaBrowserCompat.MediaItem) {
        when {
            item.isPlayable -> {
                Log.d(TAG, "isPlayable: ${item.mediaId}")

                MediaControllerCompat.getMediaController(this).transportControls
                        .playFromMediaId(item.mediaId, null)
            }
            item.isBrowsable -> navigateToBrowser(item.mediaId, item.description)
            else -> Log.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: mediaId=${item.mediaId}")
        }
    }

    override fun setToolbarTitle(title: CharSequence?) {
        collapsingToolbarLayout.title = title
        setTitle(title)
    }

    override fun enableCollapse(enable: Boolean) {
        toolbarHeader?.let {
            if (it.visibility == View.VISIBLE && !enable || it.visibility == View.GONE && enable) {
                it.visibility = if (enable) View.VISIBLE else View.GONE
                collapsingToolbarLayout.isTitleEnabled = enable
            }
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE -> {
                for (i in permissions.indices) {
                    if (Manifest.permission.WRITE_EXTERNAL_STORAGE == permissions[i] && grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        Log.d(TAG, "Permission denied!")
                        browseFragment?.showMessage(resources.getString(R.string.permission))
                    } else if (mediaBrowser.isConnected) {
                        onMediaControllerConnected()
                    }
                }
            }
        }
    }

    private fun initializeFromParams(savedInstanceState: Bundle?) {
        if (supportFragmentManager.findFragmentById(R.id.container) == null) {
            navigateToBrowser(savedInstanceState?.getString(SAVED_MEDIA_ID), null)
        }
    }

    private fun navigateToBrowser(mediaId: String?, description: MediaDescriptionCompat?) {
        Log.d(TAG, "navigateToBrowser, mediaId=$mediaId")
        var fragment: MediaBrowserFragment? = browseFragment

        if (fragment == null || !TextUtils.equals(fragment.mediaId, mediaId)) {
            fragment = MediaBrowserFragment()
            val bundle = Bundle()
            if (mediaId != null) {
                browseFragment?.mediaId?.let {
                    var path = it
                    if (mediaId.contains(QUERY) && path.contains(CATEGORY_SEPARATOR))
                        path = path.substring(0, path.indexOf(CATEGORY_SEPARATOR))
                    bundle.putString(MEDIA_ID, path + CATEGORY_SEPARATOR + mediaId)
                }
            }
            bundle.putParcelable(MediaBrowserFragment.ARG_MEDIA_DESCRIPTION, description)
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

    override fun onMediaControllerConnected() {
        super.onMediaControllerConnected()
        Log.d(TAG, "onMediaControllerConnected")
        val fragment = supportFragmentManager.findFragmentById(R.id.container)
        if (fragment is MediaBrowserFragment) {
            fragment.requestMedia()
        } else if (fragment is CueListFragment) {
            fragment.loadItems()
        }
    }

    companion object {

        const val EXTRA_START_FULLSCREEN = "com.tiefensuche.soundcrowd.EXTRA_START_FULLSCREEN"

        /**
         * Optionally used with [.EXTRA_START_FULLSCREEN] to carry a MediaDescription to
         * the [FullScreenPlayerFragment], speeding up the screen rendering
         * while the [android.support.v4.media.session.MediaControllerCompat] is connecting.
         */
        const val EXTRA_CURRENT_MEDIA_DESCRIPTION = "com.tiefensuche.soundcrowd.CURRENT_MEDIA_DESCRIPTION"
        private val TAG = MusicPlayerActivity::class.simpleName
        private const val SAVED_MEDIA_ID = "com.tiefensuche.soundcrowd.MEDIA_ID"
        private const val PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 0
        private var mPanelState: SlidingUpPanelLayout.PanelState = SlidingUpPanelLayout.PanelState.COLLAPSED
    }
}