/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.content.res.Configuration
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.app.FragmentManager
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.ui.preferences.EqualizerFragment
import com.tiefensuche.soundcrowd.utils.LogHelper
import com.tiefensuche.soundcrowd.utils.MediaIDHelper


/**
 * Abstract activity with toolbar, navigation drawer and cast support. Needs to be extended by
 * any activity that wants to be shown as a top level activity.
 *
 *
 * The requirements for a subclass is to call [.initializeToolbar] on onCreate, after
 * setContentView() is called and have three mandatory layout elements:
 * a [android.support.v7.widget.Toolbar] with id 'toolbar',
 * a [android.support.v4.widget.DrawerLayout] with id 'drawerLayout' and
 * a [android.widget.ListView] with id 'drawerList'.
 */
abstract class ActionBarCastActivity : AppCompatActivity() {

    lateinit var mToolbar: Toolbar
    lateinit var mNavigationView: NavigationView
    internal var mDrawerLayout: DrawerLayout? = null
    internal lateinit var slidingUpPanelLayout: SlidingUpPanelLayout
    private var mDrawerToggle: ActionBarDrawerToggle? = null
    private val mBackStackChangedListener = FragmentManager.OnBackStackChangedListener { this.updateDrawerToggle() }
    private val mDrawerListener = object : DrawerLayout.DrawerListener {
        override fun onDrawerClosed(drawerView: View) {
            if (mDrawerToggle != null) mDrawerToggle!!.onDrawerClosed(drawerView)
        }

        override fun onDrawerStateChanged(newState: Int) {
            if (mDrawerToggle != null) mDrawerToggle!!.onDrawerStateChanged(newState)
        }

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            if (mDrawerToggle != null) mDrawerToggle!!.onDrawerSlide(drawerView, slideOffset)
        }

        override fun onDrawerOpened(drawerView: View) {
            if (mDrawerToggle != null) mDrawerToggle!!.onDrawerOpened(drawerView)
        }
    }
    private var mToolbarInitialized: Boolean = false

    internal val browseFragment: MediaBrowserFragment?
        get() = supportFragmentManager.findFragmentByTag(MediaBrowserFragment::class.java.name) as MediaBrowserFragment?

    override fun onStart() {
        super.onStart()
        if (!mToolbarInitialized) {
            throw IllegalStateException("You must run super.initializeToolbar at " + "the end of your onCreate method")
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (mDrawerToggle != null) {
            mDrawerToggle!!.syncState()
        }
    }

    public override fun onResume() {
        super.onResume()

        // Whenever the fragment back stack changes, we may need to update the
        // action bar toggle: only top level screens show the hamburger-like icon, inner
        // screens - either Activities or fragments - show the "Up" icon instead.
        supportFragmentManager.addOnBackStackChangedListener(mBackStackChangedListener)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (mDrawerToggle != null) {
            mDrawerToggle!!.onConfigurationChanged(newConfig)
        }
    }

    public override fun onPause() {
        super.onPause()
        supportFragmentManager.removeOnBackStackChangedListener(mBackStackChangedListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (mDrawerToggle != null && mDrawerToggle!!.onOptionsItemSelected(item)) {
            return true
        }
        // If not handled by drawerToggle, home needs to be handled by returning to previous
        if (item != null && item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        // If the drawer is open, back will close it
        if (mDrawerLayout != null && mDrawerLayout!!.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout!!.closeDrawers()
            return
        }
        // If panel is expanded, collapse it
        if (slidingUpPanelLayout.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            return
        }
        // Otherwise, it may return to the previous fragment stack
        val fragmentManager = supportFragmentManager
        when {
            fragmentManager.backStackEntryCount > 0 -> fragmentManager.popBackStack()
            browseFragment?.mediaId != MediaIDHelper.MEDIA_ID_ROOT -> {
                supportFragmentManager.beginTransaction().setCustomAnimations(
                        R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                        R.animator.slide_in_from_left, R.animator.slide_out_to_right).replace(R.id.container, MediaBrowserFragment(), MediaBrowserFragment::class.java.name).commit()
                (findViewById<View>(R.id.nav_view) as NavigationView).setCheckedItem(R.id.navigation_allmusic)
            }
            else -> // Lastly, it will rely on the system behavior for back
                super.onBackPressed()
        }
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        mToolbar.title = title
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        mToolbar.setTitle(titleId)
    }

    internal fun initializeToolbar() {
        mToolbar = findViewById(R.id.toolbar)
        mToolbar.inflateMenu(R.menu.main)

        mDrawerLayout = findViewById(R.id.drawer_layout)
        if (mDrawerLayout != null) {
            mNavigationView = findViewById(R.id.nav_view)

            // Create an ActionBarDrawerToggle that will handle opening/closing of the drawer:
            mDrawerToggle = ActionBarDrawerToggle(this, mDrawerLayout,
                    mToolbar, R.string.open_content_drawer, R.string.close_content_drawer)
            mDrawerLayout!!.setDrawerListener(mDrawerListener)
            populateDrawerItems(mNavigationView)
            setSupportActionBar(mToolbar)
            updateDrawerToggle()
        } else {
            setSupportActionBar(mToolbar)
        }

        mToolbarInitialized = true
    }

    private fun populateDrawerItems(navigationView: NavigationView) {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            if (menuItem.isChecked) {
                mDrawerLayout!!.closeDrawers()
                return@setNavigationItemSelectedListener true
            }
            supportFragmentManager.popBackStack()
            menuItem.isChecked = true

            when (menuItem.itemId) {
                R.id.navigation_allmusic -> supportFragmentManager.beginTransaction().setCustomAnimations(
                        R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                        R.animator.slide_in_from_left, R.animator.slide_out_to_right).replace(R.id.container, MediaBrowserFragment(), MediaBrowserFragment::class.java.name).commit()
                R.id.navigation_cue_points -> supportFragmentManager.beginTransaction().setCustomAnimations(
                        R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                        R.animator.slide_in_from_left, R.animator.slide_out_to_right).replace(R.id.container, CueListFragment(), CueListFragment.FRAGMENT_TAG).commit()
                R.id.navigation_equalizer -> supportFragmentManager.beginTransaction().setCustomAnimations(
                        R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                        R.animator.slide_in_from_left, R.animator.slide_out_to_right).replace(R.id.container, EqualizerFragment(), EqualizerFragment.FRAGMENT_TAG).commit()
                R.id.navigation_addons -> {
                    val fragment = MediaBrowserFragment()
                    fragment.mediaId = MediaIDHelper.MEDIA_ID_PLUGINS
                    supportFragmentManager.beginTransaction().setCustomAnimations(
                            R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                            R.animator.slide_in_from_left, R.animator.slide_out_to_right).replace(R.id.container, fragment, MediaBrowserFragment::class.java.name).commit()
                }
            }
            if (slidingUpPanelLayout.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            }
            mDrawerLayout!!.closeDrawers()
            true
        }
    }

    private fun updateDrawerToggle() {
        if (mDrawerToggle == null) {
            return
        }
        val isRoot = supportFragmentManager.backStackEntryCount == 0
        mDrawerToggle!!.isDrawerIndicatorEnabled = isRoot
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayShowHomeEnabled(!isRoot)
            supportActionBar!!.setDisplayHomeAsUpEnabled(!isRoot)
            supportActionBar!!.setHomeButtonEnabled(!isRoot)
        }
        if (isRoot) {
            mDrawerToggle!!.syncState()
        }
    }

    companion object {

        private val TAG = LogHelper.makeLogTag(ActionBarCastActivity::class.java)
    }
}
