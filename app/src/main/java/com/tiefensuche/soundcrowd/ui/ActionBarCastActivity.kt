/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.color.DynamicColors
import com.google.android.material.navigation.NavigationView
import com.tiefensuche.soundcrowd.R
import com.tiefensuche.soundcrowd.sources.MusicProvider.Companion.MEDIA_ID
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.CUE_POINTS
import com.tiefensuche.soundcrowd.sources.MusicProvider.Media.PLAYLISTS
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.CATEGORY
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.ICON
import com.tiefensuche.soundcrowd.sources.MusicProvider.PluginMetadata.NAME
import com.tiefensuche.soundcrowd.ui.browser.CueMediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.ListMediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.browser.MediaBrowserFragment
import com.tiefensuche.soundcrowd.ui.preferences.EqualizerFragment
import com.tiefensuche.soundcrowd.ui.preferences.PreferenceFragment

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

    internal var mToolbar: Toolbar? = null
    private lateinit var mNavigationView: NavigationView
    private lateinit var mDrawerLayout: DrawerLayout
    internal lateinit var sheetBehavior: BottomSheetBehavior<FragmentContainerView>
    private lateinit var mDrawerToggle: ActionBarDrawerToggle

    private val paths = ArrayList<Pair<String, ArrayList<Bundle>>>()

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

    internal val currentFragmentMediaId: String?
        get() = (supportFragmentManager.findFragmentByTag(MediaBrowserFragment::class.java.name) as? MediaBrowserFragment)?.mediaId ?:
        (supportFragmentManager.findFragmentByTag(TabFragment::class.java.name) as? TabFragment)?.mediaId
    
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceFragment.applyTheme(getDefaultSharedPreferences(this).getString(getString(R.string.preference_theme_key), "System")!!)
        DynamicColors.applyToActivitiesIfAvailable(application)
        setContentView(R.layout.activity_player)
        val bottom = findViewById<FragmentContainerView>(R.id.fragment_fullscreen_player)
        sheetBehavior = BottomSheetBehavior.from(bottom)
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        initializeToolbar()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.collapsing_toolbar)) { view, insets ->
            mToolbar?.layoutParams?.height = resources.getDimensionPixelSize(R.dimen.statusbar_height) + insets.systemWindowInsetTop
            mToolbar?.setPadding(0, insets.systemWindowInsetTop, 0, 0)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        if (!mToolbarInitialized) {
            throw IllegalStateException("You must run super.initializeToolbar at the end of your onCreate method")
        }
    }

    override fun onStop() {
        super.onStop()
        mNavigationView.checkedItem?.itemId?.let {
            getDefaultSharedPreferences(this).edit()
                .putInt(getString(R.string.preference_last_fragment), it)
                .commit()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        // If not handled by drawerToggle, home needs to be handled by returning to previous
        if (item.itemId == android.R.id.home) {
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
        if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            return
        }
        super.onBackPressed()
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        mToolbar?.title = title
    }

    override fun setTitle(titleId: Int) {
        super.setTitle(titleId)
        mToolbar?.setTitle(titleId)
    }

    private fun initializeToolbar() {
        mToolbar = findViewById(R.id.toolbar)
        mToolbar?.inflateMenu(R.menu.main)

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
            // if it is the current fragment already, no need to reload
            if (menuItem.isChecked) {
                mDrawerLayout.closeDrawers()
                return true
            }

            mToolbar?.collapseActionView()
            supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            setFragmentId(menuItem.itemId)

            if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            mDrawerLayout.closeDrawers()
            return true
        })
    }

    private fun setFragmentId(id: Int) {
        when (id) {
            R.id.navigation_allmusic -> setFragment(LocalTabFragment())
            R.id.navigation_playing_queue -> setFragment(QueueFragment())
            R.id.navigation_playlists -> setFragment(ListMediaBrowserFragment().apply {
                arguments = Bundle().apply {
                    putString(MEDIA_ID, PLAYLISTS)
                    putString(NAME, "Playlists")
                }
            })
            R.id.navigation_cue_points -> setFragment(CueMediaBrowserFragment().apply {
                arguments = Bundle().apply {
                    putString(MEDIA_ID, CUE_POINTS)
                    putString(NAME, "Cue Points")
                }
            })
            R.id.navigation_equalizer -> setFragment(EqualizerFragment())
            R.id.navigation_preferences -> setFragment(PreferenceFragment())
            else -> {
                if (id > paths.size)
                    return
                // handle as addon category
                val fragment = PluginTabFragment()
                fragment.arguments = Bundle().apply {
                    putString(NAME, paths[id - 1].first)
                    putParcelableArrayList(CATEGORY, paths[id - 1].second)
                }
                setFragment(fragment)
            }
        }
        mNavigationView.setCheckedItem(id)
    }

    private fun setFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment, if (fragment is TabFragment) TabFragment::class.java.name else MediaBrowserFragment::class.java.name).commit()
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

    internal fun updatePlugins(plugins: List<Bundle>) {
        // add addon category
        val addonCategory = mNavigationView.menu.addSubMenu(getString(R.string.plugins))

        // add the categories of all addons
        for (plugin in plugins) {
            val item = addonCategory.add(Menu.NONE, paths.size + 1, paths.size, plugin.getString(NAME))
            item.isCheckable = true
            item.icon = BitmapDrawable(resources, plugin.getParcelable<Bitmap>(ICON)!!)
            paths.add(Pair(plugin.getString(NAME)!!, plugin.getParcelableArrayList(CATEGORY)!!))
        }
        setFragmentId(getDefaultSharedPreferences(this).getInt(getString(R.string.preference_last_fragment), R.id.navigation_allmusic))
    }
}