package com.pierbezuhoff.dodeca

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.OnShowRationale
import permissions.dispatcher.PermissionRequest
import permissions.dispatcher.RuntimePermissions
import java.io.File
import java.io.FileOutputStream
import kotlin.reflect.KMutableProperty0

@RuntimePermissions
class MainActivity : AppCompatActivity() {
    private var bottomBarShown = true
    private val dduDir by lazy { File(filesDir, "ddu") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // extracting assets
        if (!dduDir.exists()) {
            Log.i(TAG, "Extracting assets into $dduDir")
            // maybe: also compare their content
            dduDir.mkdir()
            extractDDUFromAssets()
        } else {
            Log.i(TAG, "$dduDir already exists")
        }
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
        // handling launch from implicit intent
        Log.i(TAG, "Dodeca started${if (intent.action == Intent.ACTION_VIEW) " from implicit intent: ${intent.data?.path ?: "-"}" else ""}")
        if (intent.action == Intent.ACTION_VIEW && (intent.type == null ||
                intent.type?.endsWith("ddu", ignoreCase = true) == true ||
                intent.data?.path?.endsWith(".ddu", ignoreCase = true) == true)) {
            intent.data?.let(::readUriWithPermissionCheck)
        }
        adjustStat()
        dodecaView.nUpdatesView = n_updates
        dodecaView.time20UpdatesView = updates_20
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
                else { // maybe: run in background
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
            R.id.app_bar_go -> toggle(dodecaView::updating)
            R.id.app_bar_next_step -> dodecaView.oneStep()
            R.id.app_bar_trace -> toggle(dodecaView::trace)
            // R.id.app_bar_change_color -> openColorPicker()
            R.id.app_bar_clear -> dodecaView.retrace()
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
                    data?.getStringExtra("path")?.let { readPathWithPermissionCheck(it) }
                }
            APPLY_SETTINGS_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val having =  { param: String, action: () -> Unit ->
                        if (data?.getBooleanExtra(param, false) == true) {
                            action()
                        }
                    }
                    adjustStat()
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun readPath(path: String) {
        Log.i(TAG, "reading ddu from path $path...")
        try {
            val file = File(path)
            dodecaView.ddu = DDU.readFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.bad_ddu_format_toast) + " $path")
        }
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun readUri(uri: Uri) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "permission WRITE_EXTERNAL_STORAGE not granted yet!")
        }
        Log.i(TAG, "reading ddu from uri $uri")
        try {
            val name = File(uri.path).name
            val targetFile = File(dduDir, name)
            val overwrite = targetFile.exists()
            // NOTE: when 'file' scheme (namely from ZArchiver) WRITE_EXTERNAL_STORAGE permission is obligatory
            val inputStream = applicationContext.contentResolver.openInputStream(uri)
            inputStream?.let {
                it.use { input ->
                    FileOutputStream(targetFile).use { input.copyTo(it, BUFFER_SIZE) }
                }
                if (overwrite) {
                    // maybe: show alert dialog
                    Log.i(TAG, "Original ddu was overwritten by imported ddu $name")
                    toast(getString(R.string.imported_ddu_overwrites_original_toast) + " $name")
                } else {
                    Log.i(TAG, "imported ddu $name")
                    toast(getString(R.string.imported_ddu_toast) + " $name")
                }
                dodecaView.ddu = DDU.readFile(targetFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.bad_ddu_format_toast) + " ${uri.path}")
        }
    }

    @OnShowRationale(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun showRationaleForReadExternalStorage(request: PermissionRequest) {
        showRationaleDialog(getString(R.string.permission_storage_message), request)
    }

    @OnPermissionDenied(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun onDeniedStorage() { toast(getString(R.string.permission_storage_accusation)) }

    private fun showRationaleDialog(message: String, request: PermissionRequest) {
        alert(message) {
            title = getString(R.string.permission_rationale_dialog_title)
            positiveButton(getString(R.string.permission_rationale_dialog_allow)) { request.proceed() }
            negativeButton(getString(R.string.permission_rationale_dialog_deny)) { request.cancel() }
            isCancelable = false
        }.show()
    }

    private fun adjustStat() {
        if (dodecaView.sharedPreferences.getBoolean("show_stat", false)) {
            stat.visibility = LinearLayout.VISIBLE
        } else {
            stat.visibility = LinearLayout.GONE
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
            // dodecaView.systemUiVisibility = FULLSCREEN_UI_VISIBILITY
        }
        toggle(::bottomBarShown)
    }

    private fun extractDDUFromAssets() {
        val dir = dduDir
        assets.list("ddu")?.forEach { name -> extract1DDU(name, dir) }
    }

    private fun extract1DDU(name: String, dir: File = dduDir) {
        val source = "ddu/$name"
        val targetFile = File(dir, name)
        if (targetFile.createNewFile())
            Log.i(TAG, "Copying asset $source to ${targetFile.path}")
        else
            Log.i(TAG, "Overwriting ${targetFile.path} by asset $source")
        assets.open(source).use { input ->
            FileOutputStream(targetFile).use { input.copyTo(it, BUFFER_SIZE) }
        }
    }

    companion object {
        const val TAG = "MainActivity"
        const val BUFFER_SIZE = DEFAULT_BUFFER_SIZE
        const val DDU_CODE = 1
        const val APPLY_SETTINGS_CODE = 2
        // fullscreen, but with bottom navigation
        const val FULLSCREEN_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN
        // distraction free
        const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}

internal fun toggle(prop: KMutableProperty0<Boolean>) {
    prop.set(!prop.get())
}
