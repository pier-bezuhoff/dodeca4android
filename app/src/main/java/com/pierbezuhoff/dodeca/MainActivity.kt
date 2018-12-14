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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast
import permissions.dispatcher.*
import java.io.File
import java.io.FileOutputStream
import kotlin.reflect.KMutableProperty0

@RuntimePermissions
class MainActivity : AppCompatActivity() /*, ActivityCompat.OnRequestPermissionsResultCallback*/ {
    private var bottomBarShown = true
    private val dduDir by lazy { File(filesDir, "ddu") }

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
        Log.i(TAG, "Dodeca started${if (intent.action == Intent.ACTION_VIEW) " from implicit intent: ${intent.data?.path ?: "-"}" else ""}")
        if (intent.action == Intent.ACTION_VIEW && (intent.type == null ||
                intent.type?.endsWith("ddu", ignoreCase = true) == true ||
                intent.data?.path?.endsWith(".ddu", ignoreCase = true) == true)) {
            intent.data?.let { readUri(it) }
        }
        if (!dduDir.exists()) {
            Log.i(TAG, "Extracting assets into $dduDir")
            dduDir.mkdir()
            extractDDUFromAssets()
        } else {
            Log.i(TAG, "$dduDir already exists")
        }
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            Log.i(TAG, "permission group STORAGE not granted")
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST_CODE)
//        }
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
                    data?.getStringExtra("path")?.let { readPath(it) }
                }
            APPLY_SETTINGS_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val having =  { param: String, action: () -> Unit ->
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                permissions.zip(grantResults.asIterable()).forEach { (permission, grantResult) ->
                    if (grantResult == PackageManager.PERMISSION_GRANTED)
                        Log.i(TAG, "permission $permission granted")
                    else
                        Log.i(TAG, "permission $permission rejected")
                }
            }
        }
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun readPath(path: String) {
        Log.i(TAG, "reading ddu from path $path...")
        try {
            val file = File(path)
            dodecaView.ddu = DDU.readFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.bad_ddu_format_toast) + path)
        }
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun readUri(uri: Uri) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "permission WRITE_EXTERNAL_STORAGE not granted yet!top" +
                "t")
        }
//        needPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        Log.i(TAG, "reading ddu from uri $uri")
        try {
            val name = File(uri.path).name
            val targetFile = File(dduDir, name)
            val overwrite = targetFile.exists()
            // BUG: works with 'content' scheme, permission denied with 'file'
            // until in seetings > permission > storage > dodeca > +
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
            toast(getString(R.string.bad_ddu_format_toast) + uri.path)
        }
    }

    @OnShowRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun showRationaleForReadExternalStorage(request: PermissionRequest) {
        showRationaleDialog("to import ddu", request)
    }

    @OnNeverAskAgain(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun onNeverAskAgainStorage() { toast("never? bad!") }

    @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun onDeniedStorage() { toast("why? you cannot import ddu now!") }

    private fun needPermission(permission: String) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "permission $permission not granted yet")
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission))
                0 // show smth
//                showRationaleDialog("to import ddu")
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }

    private fun showRationaleDialog(message: String, request: PermissionRequest) {
        alert(message) {
            title = "Permission request"
            positiveButton("Allow") { request.proceed() }
            negativeButton("Deny") { request.cancel() }
            isCancelable = false
        }.show()
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
        toggle(::bottomBarShown)
    }

    private fun extractDDUFromAssets() {
        val dir = dduDir
        assets.list("ddu")?.forEach { name ->
            extract1DDU(name, dir)
        }
    }

    private fun extract1DDU(name: String, dir: File = dduDir) {
        val source = "ddu/$name"
        val targetFile = File(dir, name)
        targetFile.createNewFile()
        Log.i(TAG, "Copying asset $source to ${targetFile.path}")
        assets.open(source).use { input ->
            FileOutputStream(targetFile).use { input.copyTo(it, BUFFER_SIZE) }
        }
    }

    companion object {
        const val TAG = "MainActivity"
        const val BUFFER_SIZE = DEFAULT_BUFFER_SIZE
        const val DDU_CODE = 1
        const val APPLY_SETTINGS_CODE = 2
        const val PERMISSION_REQUEST_CODE = 3
        // fullscreen, but with bottom navigation
        const val FULLSCREEN_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN
        // distraction free
        const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}

internal fun toggle(prop: KMutableProperty0<Boolean>) {
    prop.set(!prop.get())
}