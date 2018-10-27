package com.pierbezuhoff.dodeca

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.NavigationView
import android.support.v4.view.GestureDetectorCompat
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.jetbrains.anko.toast
import java.io.FileInputStream
import java.lang.Exception

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var gestureDetector: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)
        val gestureListener = DodecaGestureListener(dodecaView)
        gestureDetector = GestureDetectorCompat(this@MainActivity, gestureListener)
        gestureDetector.setOnDoubleTapListener(gestureListener)
        dodecaView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_settings -> return true
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_load -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*" // should be .ddu
                }
                startActivityForResult(Intent.createChooser(intent, "Select .ddu"), DDU_CODE)
            }
            R.id.nav_go -> {
                dodecaView.thread.redraw = !dodecaView.thread.redraw
            }
            R.id.nav_trace -> {
                dodecaView.trace = !dodecaView.trace
            }
//            R.id.nav_manage -> { }
//            R.id.nav_send -> { }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DDU_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                try {
                    dodecaView.ddu = DDU.read(
                        FileInputStream(contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor))
                } catch (e: Exception) {
                    e.printStackTrace()
                    toast("bad .ddu: $uri")
                }
            }
        }
    }

    companion object {
        const val DDU_CODE = 1
    }
}
