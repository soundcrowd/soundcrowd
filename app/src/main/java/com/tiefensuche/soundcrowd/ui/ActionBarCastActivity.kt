/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.media.MediaBrowserCompat
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
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.ui.preferences.EqualizerFragment
import com.tiefensuche.soundcrowd.ui.preferences.PreferenceFragment
import com.tiefensuche.soundcrowd.utils.MediaIDHelper
import org.json.JSONArray


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

    internal lateinit var mToolbar: Toolbar
    internal lateinit var mNavigationView: NavigationView
    private lateinit var mDrawerLayout: DrawerLayout
    internal lateinit var slidingUpPanelLayout: SlidingUpPanelLayout
    private lateinit var mDrawerToggle: ActionBarDrawerToggle

    internal var plugins: List<MediaBrowserCompat.MediaItem> = ArrayList()

    private val mBackStackChangedListener = FragmentManager.OnBackStackChangedListener { this.updateDrawerToggle() }

    private val mDrawerListener = object : DrawerLayout.DrawerListener {
        override fun onDrawerClosed(drawerView: View) {
            mDrawerToggle.onDrawerClosed(drawerView)
        }

        override fun onDrawerStateChanged(newState: Int) {
            mDrawerToggle.onDrawerStateChanged(newState)
        }

        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            mDrawerToggle.onDrawerSlide(drawerView, slideOffset)
        }

        override fun onDrawerOpened(drawerView: View) {
            mDrawerToggle.onDrawerOpened(drawerView)
        }
    }
    private var mToolbarInitialized: Boolean = false

    internal val browseFragment: MediaBrowserFragment?
        get() = supportFragmentManager.findFragmentByTag(MediaBrowserFragment::class.java.name) as? MediaBrowserFragment

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        initializeToolbar()
    }

    override fun onStart() {
        super.onStart()
        if (!mToolbarInitialized) {
            throw IllegalStateException("You must run super.initializeToolbar at " + "the end of your onCreate method")
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle.syncState()
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
        mDrawerToggle.onConfigurationChanged(newConfig)
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
        if (mDrawerToggle.onOptionsItemSelected(item)) {
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
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers()
            return
        }
        // If panel is expanded, collapse it
        if (slidingUpPanelLayout.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
            slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            return
        }
        // Otherwise, it may return to the previous fragment stack
        when {
            supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
            browseFragment?.mediaId != MediaIDHelper.MEDIA_ID_ROOT -> {
                setFragment(MediaBrowserFragment())
                mNavigationView.setCheckedItem(R.id.navigation_allmusic)
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

    private fun initializeToolbar() {
        mToolbar = findViewById(R.id.toolbar)
        mToolbar.inflateMenu(R.menu.main)

        mDrawerLayout = findViewById(R.id.drawer_layout)

        mNavigationView = findViewById(R.id.nav_view)

        // Create an ActionBarDrawerToggle that will handle opening/closing of the drawer:
        mDrawerToggle = ActionBarDrawerToggle(this, mDrawerLayout,
                mToolbar, R.string.open_content_drawer, R.string.close_content_drawer)
        mDrawerLayout.setDrawerListener(mDrawerListener)
        populateDrawerItems(mNavigationView)
        setSupportActionBar(mToolbar)
        updateDrawerToggle()

        mToolbarInitialized = true
    }

    private fun populateDrawerItems(navigationView: NavigationView) {

        navigationView.setNavigationItemSelectedListener(fun(menuItem: MenuItem): Boolean {
            // if it is the current fragment already, not need to reload
            if (menuItem.isChecked) {
                mDrawerLayout.closeDrawers()
                return true
            }

            supportFragmentManager.popBackStack()

            when (menuItem.itemId) {
                R.id.navigation_allmusic -> setFragment(MediaBrowserFragment())
                R.id.navigation_cue_points -> setFragment(CueListFragment())
                R.id.navigation_equalizer -> setFragment(EqualizerFragment())
                R.id.navigation_preferences -> {
                    val fragment = PreferenceFragment()
                    val prefs = HashMap<String, JSONArray>()
                    for (plugin in plugins) {
                        plugin.description.extras?.getString(MediaMetadataCompatExt.METADATA_KEY_PREFERENCES)?.let { json ->
                            prefs.put(plugin.description.subtitle.toString(), JSONArray(json))
                        }
                    }
                    fragment.prefs = prefs
                    setFragment(fragment)
                }
                else -> { // handle as addon category
                    val fragment = MediaBrowserFragment()
                    val args = Bundle()
                    args.putParcelable(MediaBrowserFragment.ARG_MEDIA_DESCRIPTION, plugins[menuItem.itemId].description)
                    fragment.arguments = args
                    setFragment(fragment)
                }
            }

            if (slidingUpPanelLayout.panelState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                slidingUpPanelLayout.panelState = SlidingUpPanelLayout.PanelState.COLLAPSED
            }
            mDrawerLayout.closeDrawers()
            return true
        })
    }

    private fun setFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment, fragment::class.java.name).commit()
    }

    private fun updateDrawerToggle() {
        val isRoot = supportFragmentManager.backStackEntryCount == 0
        mDrawerToggle.isDrawerIndicatorEnabled = isRoot
        supportActionBar?.let {
            it.setDisplayShowHomeEnabled(!isRoot)
            it.setDisplayHomeAsUpEnabled(!isRoot)
            it.setHomeButtonEnabled(!isRoot)
        }
        if (isRoot) {
            mDrawerToggle.syncState()
        }
    }

    internal fun updatePlugins(plugins: List<MediaBrowserCompat.MediaItem>) {
        this.plugins = plugins

        // add addon category
        val addonCategory = mNavigationView.menu.addSubMenu("Addons")

        // add the categories of all addons
        for ((i, plugin) in plugins.withIndex()) {
            val item = addonCategory.add(0, i, 0, plugin.description.title)
            item.isCheckable = true
            item.icon = BitmapDrawable(resources, plugin.description.iconBitmap)
        }
    }
}
