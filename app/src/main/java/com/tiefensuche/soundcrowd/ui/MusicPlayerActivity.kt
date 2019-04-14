/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.CollapsingToolbarLayout
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.view.MenuItemCompat
import android.support.v7.widget.SearchView
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.intro.ShowcaseViewManager
import com.tiefensuche.soundcrowd.utils.LogHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.CATEGORY_SEPARATOR
import com.tiefensuche.soundcrowd.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH


/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
internal class MusicPlayerActivity : BaseActivity(), MediaBrowserFragment.MediaFragmentListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var searchView: SearchView
    private lateinit var searchItem: MenuItem
    internal lateinit var controls: RelativeLayout
    internal lateinit var collapsingToolbarLayout: CollapsingToolbarLayout
    private lateinit var toolbarHeader: View
    private lateinit var preferences: SharedPreferences

    private val mediaId: String?
        get() {
            val fragment = browseFragment ?: return null
            return fragment.mMediaId
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "create activity")

        initializeToolbar()
        initializeFromParams(savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        searchItem = mToolbar.menu.findItem(R.id.action_search)
        controls = findViewById(R.id.controls)
        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar)
        toolbarHeader = findViewById(R.id.toolbar_header)

        val playPauseButton = findViewById<ImageView>(R.id.play_pause)
        val controlsContainer = findViewById<RelativeLayout>(R.id.controls_layout)

        slidingUpPanelLayout = findViewById(R.id.sliding_layout)
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

        // Only check if a full screen player is needed on the first time:
        if (savedInstanceState == null) {
            startFullScreenActivityIfNeeded(intent)
        }

        checkPermissions()

        if (mPanelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            controls.alpha = 0f
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

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

        searchView = MenuItemCompat.getActionView(searchItem) as? SearchView ?: throw RuntimeException()
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                LogHelper.d(TAG, "onQueryTextSubmit, query=", query)
                browseFragment?.let {
                    navigateToBrowser(it.mMediaId + CATEGORY_SEPARATOR +
                            MEDIA_ID_MUSICS_BY_SEARCH + CATEGORY_SEPARATOR + query, null)
                    closeSearchMenu()
                    searchView.clearFocus()
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

    public override fun onStart() {
        super.onStart()
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val mediaId = mediaId

        if (mediaId != null) {
            outState.putString(SAVED_MEDIA_ID, mediaId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onMediaItemSelected(item: MediaBrowserCompat.MediaItem) {
        when {
            item.isPlayable -> {
                LogHelper.d(TAG, "isPlayable: " + item.mediaId)

                MediaControllerCompat.getMediaController(this).transportControls
                        .playFromMediaId(item.mediaId, null)

            }
            item.isBrowsable -> navigateToBrowser(item.mediaId, item.description)
            else -> LogHelper.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: ",
                    "mediaId=", item.mediaId)
        }
    }

    override fun setToolbarTitle(title: CharSequence?) {
        LogHelper.d(TAG, "Setting toolbar title to ", title)
        collapsingToolbarLayout.title = title
        setTitle(title)
    }

    override fun enableCollapse(enable: Boolean) {
        if (toolbarHeader.visibility == View.VISIBLE && !enable || toolbarHeader.visibility == View.GONE && enable) {
            toolbarHeader.visibility = if (enable) View.VISIBLE else View.GONE
            collapsingToolbarLayout.isTitleEnabled = enable
        }
    }

    override fun onNewIntent(intent: Intent) {
        LogHelper.d(TAG, "onNewIntent, intent=$intent")
        startFullScreenActivityIfNeeded(intent)
    }

    private fun checkPermissions() {
        preferences.registerOnSharedPreferenceChangeListener(this)
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
                        LogHelper.d(TAG, "Permission denied!")
                        Snackbar.make(findViewById(R.id.controls), resources.getString(R.string.permission), Snackbar.LENGTH_INDEFINITE).show()
                    } else if (mediaBrowser.isConnected) {
                        onMediaControllerConnected()
                    }
                }
            }
        }
    }

    private fun startFullScreenActivityIfNeeded(intent: Intent?) {
        if (intent != null && intent.getBooleanExtra(EXTRA_START_FULLSCREEN, false)) {
            showPlaybackControls()
            slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.EXPANDED
        }
    }

    private fun initializeFromParams(savedInstanceState: Bundle?) {
        var mediaId: String? = null
        if (savedInstanceState != null) {
            // If there is a saved media ID, use it
            mediaId = savedInstanceState.getString(SAVED_MEDIA_ID)
        }
        if (supportFragmentManager.findFragmentById(R.id.container) == null) {
            navigateToBrowser(mediaId, null)
        }
    }

    private fun navigateToBrowser(mediaId: String?, description: MediaDescriptionCompat?) {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=", mediaId)
        var fragment: MediaBrowserFragment? = browseFragment

        if (fragment == null || !TextUtils.equals(fragment.mMediaId, mediaId)) {
            fragment = MediaBrowserFragment()
            val bundle = Bundle()
            bundle.putString("media_id", mediaId)
            bundle.putBoolean("stream", description?.extras?.getBoolean("stream") ?: false)
            fragment.arguments = bundle
            fragment.setDescription(description)
            val transaction = supportFragmentManager.beginTransaction()
            transaction.setCustomAnimations(
                    R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                    R.animator.slide_in_from_left, R.animator.slide_out_to_right)
            transaction.replace(R.id.container, fragment, MediaBrowserFragment::class.java.name)
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            if (mediaId != null) {
                transaction.addToBackStack(null)
            }
            transaction.commit()
        }
    }

    override fun closeSearchMenu() {
        //        MenuItemCompat.collapseActionView(searchItem);
    }

    override fun showSearchButton(show: Boolean) {
        searchItem.isVisible = show
    }

    override fun onMediaControllerConnected() {
        super.onMediaControllerConnected()
        LogHelper.d(TAG, "onMediaControllerConnected")
        val fragment = supportFragmentManager.findFragmentById(R.id.container)
        if (fragment is MediaBrowserFragment) {
            fragment.onConnected()
        } else if (fragment is CueListFragment) {
            fragment.loadItems()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, s: String) {
        checkPermissions()
    }

    companion object {

        const val EXTRA_START_FULLSCREEN = "com.tiefensuche.soundcrowd.EXTRA_START_FULLSCREEN"
        /**
         * Optionally used with [.EXTRA_START_FULLSCREEN] to carry a MediaDescription to
         * the [FullScreenPlayerFragment], speeding up the screen rendering
         * while the [android.support.v4.media.session.MediaControllerCompat] is connecting.
         */
        const val EXTRA_CURRENT_MEDIA_DESCRIPTION = "com.tiefensuche.soundcrowd.CURRENT_MEDIA_DESCRIPTION"
        private val TAG = LogHelper.makeLogTag(MusicPlayerActivity::class.java)
        private const val SAVED_MEDIA_ID = "com.tiefensuche.soundcrowd.MEDIA_ID"
        private const val PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 0
        private var mPanelState: SlidingUpPanelLayout.PanelState = SlidingUpPanelLayout.PanelState.COLLAPSED
    }
}
