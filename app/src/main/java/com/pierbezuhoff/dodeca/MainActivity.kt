package com.pierbezuhoff.dodeca

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GestureDetectorCompat
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.util.AttributeSet
import android.view.*
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.io.FileInputStream
import java.lang.Exception

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private var bottomBarShown = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.apply {
            systemUiVisibility = FULLSCREEN_UI_VISIBILITY
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)
        setSupportActionBar(bar)
        // TODO: replace drawer layout with SettingsActivity or smth
//        val toggle = ActionBarDrawerToggle(
//            this, drawer_layout, bar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
//        )
//        drawer_layout.addDrawerListener(toggle)
//        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)
        // listen scroll, double tap and scale gestures
        DodecaGestureDetector(this, dodecaView, onSingleTap = { toggleBottomBar() })
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.bottomappbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.app_bar_load -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*" // should be .ddu
                }
                startActivityForResult(Intent.createChooser(intent, "Select .ddu"), DDU_CODE)
            }
            R.id.app_bar_save -> {
                /* TODO: change circles' x, y, radius with respect to
                dodecaView's dx, dy, scale and save (as)*/
            }
            R.id.app_bar_go -> {
                dodecaView.updating = !dodecaView.updating
            }
            R.id.app_bar_trace -> {
                dodecaView.trace = !dodecaView.trace
            }
            R.id.app_bar_settings -> {}
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {}
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DDU_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                try {
                    // TODO: save filename or whatisit
                    dodecaView.ddu = DDU.read(
                        FileInputStream(contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast("bad .ddu: $uri")
                }
            }
        }
    }

    private fun toggleBottomBar() {
        if (bottomBarShown) {
            bar.visibility = View.GONE
            dodecaView.systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        }
        else {
            bar.visibility = View.VISIBLE
            dodecaView.systemUiVisibility = FULLSCREEN_UI_VISIBILITY
        }
        bottomBarShown = !bottomBarShown
    }

    companion object {
        const val DDU_CODE = 1
        const val FULLSCREEN_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN
        const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }
}
