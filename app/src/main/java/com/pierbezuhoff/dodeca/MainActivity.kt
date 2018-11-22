package com.pierbezuhoff.dodeca

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.io.FileInputStream

class MainActivity : AppCompatActivity() {
    private var bottomBarShown = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.apply {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY // FULLSCREEN_UI_VISIBILITY
            setOnSystemUiVisibilityChangeListener {
                if ((it and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                    this.systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            }
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        setContentView(R.layout.activity_main)
        setSupportActionBar(bar)

        // listen scroll, double tap and scale gestures
        DodecaGestureDetector(this, dodecaView, onSingleTap = { toggleBottomBar() })
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
                dodecaView's dx, dy, scale and save (as) */
                // dodecaView.save()
            }
            R.id.app_bar_go -> {
                dodecaView.updating = !dodecaView.updating
            }
            R.id.app_bar_next_step -> dodecaView.oneStep()
            R.id.app_bar_trace -> {
                dodecaView.trace = !dodecaView.trace
            }
            R.id.app_bar_clear -> {
                dodecaView.retrace()
            }
            R.id.app_bar_settings -> {
                startActivityForResult(
                    Intent(this@MainActivity, SettingsActivity::class.java),
                    APPLY_SETTINGS_CODE)
            }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            DDU_CODE ->
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.also { uri ->
                        try {
                            dodecaView.ddu = DDU.readStream(FileInputStream(
                                contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor))
                        } catch (e: Exception) {
                            e.printStackTrace()
                            toast("bad .ddu: $uri")
                        }
                    }
                }
            APPLY_SETTINGS_CODE ->
                dodecaView.loadMajorSharedPreferences()
        }
        dodecaView.systemUiVisibility = IMMERSIVE_UI_VISIBILITY
    }

    private fun toggleBottomBar() {
        if (bottomBarShown) {
            bar.visibility = View.GONE
            dodecaView.systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        }
        else {
            bar.visibility = View.VISIBLE
            // don't let that sticky bottom nav. return, always immersive
//            dodecaView.systemUiVisibility = FULLSCREEN_UI_VISIBILITY
        }
        bottomBarShown = !bottomBarShown
    }

    companion object {
        const val TAG = "MainActivity"
        const val DDU_CODE = 1
        const val APPLY_SETTINGS_CODE = 2
        // fullscreen, but with bottom navigation
        const val FULLSCREEN_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN
        // distraction free
        const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
