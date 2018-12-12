package com.pierbezuhoff.dodeca

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private var bottomBarShown = true
    private val dduDir get() = File(filesDir, "ddu")

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
        // handle outer implicit intent
        if (intent.action == Intent.ACTION_VIEW && intent.type?.endsWith("ddu") == true) {
            intent.data?.path?.let { readPath(it) }
        } else {
            if (!dduDir.exists()) {
                Log.i(TAG, "Extracting assets into $dduDir")
                dduDir.mkdir()
                extractDDUFromAssets()
            } else {
                Log.i(TAG, "$dduDir already exists")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.bottomappbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.app_bar_load -> {
                val intent = Intent(this, DDUChooserActivity::class.java)
                intent.putExtra("dirPath", dduDir.absolutePath)
                startActivityForResult(intent, DDU_CODE)
            }
            R.id.app_bar_save -> {
                val ddu = dodecaView.prepareDDUToSave()
                if (ddu.file == null) {
                    // then save as
                    toast(getString(R.string.error_ddu_save_no_file_toast))
                }
                else {
                    try {
                        ddu.file?.let { file ->
                            Log.i(TAG, "Saving ddu at at ${file.path}")
                            ddu.saveStream(file.outputStream())
                            toast(getString(R.string.ddu_saved_toast) + " ${file.name}")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        toast(getString(R.string.error_ddu_save_toast))
                    }
                }
            }
            R.id.app_bar_go -> {
                dodecaView.updating = !dodecaView.updating
            }
            R.id.app_bar_next_step -> dodecaView.oneStep()
            R.id.app_bar_trace -> {
                dodecaView.trace = !dodecaView.trace
            }
            // R.id.app_bar_change_color -> openColorPicker()
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
                    data?.getStringExtra("path")?.let { readPath(it) }
                }
            APPLY_SETTINGS_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val having = { param: String, action: () -> Unit ->
                        if (data?.getBooleanExtra(param, false) == true) {
                            action()
                        }
                    }
                    having("autocenter") { DodecaView.autocenterOnce = true }
                    having("default_ddu") {
                        dodecaView.ddu.file?.let { file ->
                            extract1DDU(file.name)
                            dodecaView.ddu = DDU.readFile(file)
                        }
                    }
                    having("default_ddus") { extractDDUFromAssets() }
                }
                dodecaView.loadMajorSharedPreferences()
            }
        }
        dodecaView.systemUiVisibility = IMMERSIVE_UI_VISIBILITY
    }

    private fun readPath(path: String) {
        try {
            val file = File(path)
            dodecaView.ddu = DDU.readFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.bad_ddu_format_toast) + path)
        }
    }

    fun openColorPicker() {
        val fromColor = dodecaView.pickedColor
        if (fromColor != null) {
            // TODO: fromColor -> chooseNewColor -> dodecaView.changeColor(it)
        } else {
            toast(getString(R.string.please_pick_color_toast))
        }
    }

    private fun toggleBottomBar() {
        if (bottomBarShown) {
            bar.visibility = View.GONE
            dodecaView.systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        }
        else {
            bar.visibility = View.VISIBLE
            // don't let that sticky bottom nav. return, always immersive
            // dodecaView.systemUiVisibility = FULLSCREEN_UI_VISIBILITY
        }
        bottomBarShown = !bottomBarShown
    }

    private fun extractDDUFromAssets() {
        val bufferSize = 1024
        val dir = dduDir
        assets.list("ddu")?.forEach { name ->
            extract1DDU(name, dir, bufferSize)
        }
    }

    private fun extract1DDU(name: String, dir: File = dduDir, bufferSize: Int = 1024) {
        val source = "ddu/$name"
        val targetFile = File(dir, name)
        targetFile.createNewFile()
        Log.i(TAG, "Copying asset $source to ${targetFile.path}")
        assets.open(source).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output, bufferSize)
            }
        }
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
