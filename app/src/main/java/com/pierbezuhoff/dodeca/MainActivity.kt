package com.pierbezuhoff.dodeca

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.children
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.toolbar1.*
import kotlinx.android.synthetic.main.toolbar2.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.layoutInflater
import org.jetbrains.anko.toast
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.OnShowRationale
import permissions.dispatcher.PermissionRequest
import permissions.dispatcher.RuntimePermissions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Timer
import kotlin.concurrent.timerTask

@RuntimePermissions
class MainActivity : AppCompatActivity() {
    private var bottomBarShown = true
    private val dduDir by lazy { File(filesDir, "ddu") }
    private var bottomBarHideTimer: Timer? = null
    private var updatingBeforePause: Boolean? = null
    private val dduFileDao: DDUFileDao by lazy { DB.dduFileDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Options(resources)
        DDUFileDatabase.init(this)
        // extracting assets
        if (!dduDir.exists()) {
            Log.i(TAG, "Extracting assets into $dduDir")
            dduDir.mkdir()
            extractDDUFromAssets()
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
        supportActionBar?.setDisplayShowTitleEnabled(false)
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
        dodecaView.afterNewDDU = { afterNewDDU(it) }
        setupToolbar()
        hideBottomBarAfterAWhile()
    }

    private fun afterNewDDU(ddu: DDU) {
        shape_spinner.setSelection(ddu.shape.ordinal)
        mapOf(
            options.drawTrace to trace_button,
            options.showOutline to outline_button
        ).forEach { (preference, button) ->
            setupToggleButtonTint(button, preference.value)
        }
        setupPlayButton()
    }

    private fun setupToolbar() {
        setOf(toolbar1, toolbar2).forEach { toolbar ->
            toolbar.children.filterIsInstance(ImageButton::class.java).forEach { button ->
                TooltipCompat.setTooltipText(button, button.contentDescription)
                button.setOnClickListener { onToolbarItemClick(it.id) }
            }
        }
        // ISSUE: on spinner dialog: stop bottom bar timer, pause dodecaView
        // BUG: after BOTTOM_BAR_HIDE_DELAY selection does not work!
        with(shape_spinner) {
            TooltipCompat.setTooltipText(this, this.contentDescription)
            adapter = ShapeSpinnerAdapter(context)
            setSelection(0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) { showBottomBar() }
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    dodecaView.change(options.shape, Shapes.indexOrFirst(id.toInt()))
                    showBottomBar()
                }
            }
        }
    }

    private inline fun setupToggleButtonTint(button: ImageButton, value: Boolean) {
        (button as AppCompatImageButton).supportBackgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(
            this,
            if (value) R.color.darkerToolbarColor
            else R.color.toolbarColor
        ))
    }

    private inline fun setupPlayButton() {
        play_button.setImageDrawable(ContextCompat.getDrawable(
            this,
            if (options.updating.value) R.drawable.ic_pause
            else R.drawable.ic_play
        ))
    }

    private fun onToolbarItemClick(id: Int) {
        showBottomBar()
        when (id) {
            R.id.help_button -> {
                val intent = Intent(this, HelpActivity::class.java)
                startActivityForResult(intent, HELP_CODE)
            }
            R.id.load_button -> {
                val intent = Intent(this, DDUChooserActivity::class.java)
                intent.putExtra("dirPath", dduDir.absolutePath)
                startActivityForResult(intent, DDU_CODE)
            }
            R.id.save_button -> dodecaView.saveDDU()
            R.id.play_button -> { dodecaView.toggle(options.updating); setupPlayButton() }
            R.id.next_step_button -> { dodecaView.oneStep(); setupPlayButton() }
            R.id.trace_button -> { dodecaView.toggle(options.drawTrace); setupToggleButtonTint(trace_button, options.drawTrace.value) }
            R.id.outline_button -> { dodecaView.toggle(options.showOutline); setupToggleButtonTint(outline_button, options.showOutline.value) }
            // R.id.change_color_button -> ...
            R.id.clear_button -> dodecaView.retrace()
            R.id.autocenter_button -> dodecaView.autocenter()
            R.id.settings_button -> {
                startActivityForResult(
                    Intent(this@MainActivity, SettingsActivity::class.java),
                    APPLY_SETTINGS_CODE)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatingBeforePause?.let {
            dodecaView.change(options.updating, it)
            setupPlayButton()
        }
    }

    override fun onPause() {
        super.onPause()
        updatingBeforePause = options.updating.value
        dodecaView.change(options.updating, false)
        if (options.autosave.value && dodecaView.ddu.file != null)
            dodecaView.saveDDU()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // orientation and screenSize
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            DDU_CODE ->
                if (resultCode == Activity.RESULT_OK) {
                    data?.getStringExtra("path")?.let {
                        updatingBeforePause = null
                        readPath(it)
                    }
                }
            APPLY_SETTINGS_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val having =  { param: String, action: () -> Unit ->
                        if (data?.getBooleanExtra(param, false) == true)
                            action()
                    }
                    adjustStat()
                    having("default_ddu") {
                        dodecaView.ddu.file?.let { file ->
                            extract1DDU(file.name)
                            dodecaView.ddu = DDU.readFile(file)
                        }
                    }
                    having("default_ddus") {
                        extractDDUFromAssets()
                        dodecaView.ddu.file?.let { file -> dodecaView.ddu = DDU.readFile(file) }
                    }
                    having("discard_previews") {
                        dduFileDao.getAll().forEach { dduFileDao.update(it.apply { preview = null }) }
                    }
                }
                dodecaView.loadMajorSharedPreferences()
            }
            HELP_CODE -> Unit
        }
        showBottomBar()
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
                if (overwrite) {Unit
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
            dodecaView.showStat = true
            stat.visibility = LinearLayout.VISIBLE
        } else {
            stat.visibility = LinearLayout.GONE
            dodecaView.showStat = false
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

    private fun hideBottomBarAfterAWhile() {
        bottomBarHideTimer = Timer("bottomBarHideTimer")
        bottomBarHideTimer?.schedule(timerTask {
            bar.post { hideBottomBar() }
            bottomBarHideTimer = null
        }, 1000L * BOTTOM_BAR_HIDE_DELAY)
    }

    private fun hideBottomBar() {
        bar.visibility = View.GONE
        dodecaView.systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        bottomBarShown = false
        bottomBarHideTimer?.cancel()
    }

    private fun showBottomBar() {
        bottomBarHideTimer?.cancel()
        bar.visibility = View.VISIBLE
        dodecaView.systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        hideBottomBarAfterAWhile()
        bottomBarShown = true
    }

    private fun toggleBottomBar() =
        if (bottomBarShown) hideBottomBar()
        else showBottomBar()

    fun extractDDUFromAssets() {
        val dir = dduDir
        assets.list("ddu")?.forEach { name -> extract1DDU(name, dir) }
    }

    fun extract1DDU(filename: Filename, dir: File = dduDir) = extract1DDU(filename, dir, dduFileDao, TAG)

    class ShapeSpinnerAdapter(val context: Context) : BaseAdapter() {
        val shapes: Array<Int> = arrayOf( // the same order as in Circle.kt/Shapes
            R.drawable.ic_circle,
            R.drawable.ic_square,
            R.drawable.ic_cross,
            R.drawable.ic_vertical_bar,
            R.drawable.ic_horizontal_bar
        )
        private class SpinnerViewHolder(val imageView: ImageView)
        override fun getItem(position: Int): Any = shapes[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getCount(): Int = shapes.size

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val itemView: View = convertView ?:
            context.layoutInflater.inflate(R.layout.shape_spinner_row, parent, false).apply {
                tag = SpinnerViewHolder(findViewById(R.id.shape_spinner_image))
            }
            (itemView.tag as SpinnerViewHolder).imageView.setImageDrawable(
                ContextCompat.getDrawable(context, shapes[position]))
            return itemView
        }
    }

    companion object {
        const val TAG = "MainActivity"
        const val LIMITED_VERSION = false
        const val BUFFER_SIZE = DEFAULT_BUFFER_SIZE
        const val DDU_CODE = 1
        const val APPLY_SETTINGS_CODE = 2
        const val HELP_CODE = 3
        // fullscreen, but with bottom navigation
        const val FULLSCREEN_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN
        // distraction free
        const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        const val BOTTOM_BAR_HIDE_DELAY = 30 // seconds
    }
}

fun Context.extract1DDU(filename: Filename, dir: File, dduFileDao: DDUFileDao, TAG: String = MainActivity.TAG): Filename? {
    var source: Filename = filename
    fun streamFromDDUAsset(filename: Filename): InputStream = assets.open("ddu/$filename")
    val inputStream: InputStream? = try {
        streamFromDDUAsset(source)
    } catch (e: IOException) {
        dduFileDao.findByFilename(filename)?.let {
            source = it.originalFilename
            try {
                streamFromDDUAsset(source)
            } catch (e: IOException) { null }
        }
    }
    val success = inputStream?.let {
        val targetFile = File(dir, source)
        if (targetFile.createNewFile()) {
            Log.i(TAG, "Copying asset $source to ${targetFile.path}")
        } else {
            Log.i(TAG, "Overwriting ${targetFile.path} by asset $source")
        }
        inputStream.use { input ->
            FileOutputStream(targetFile).use { input.copyTo(it, MainActivity.BUFFER_SIZE) }
        }
        dduFileDao.insertOrUpdate(source) { preview = null }
    }
    return if (success != null) source
    else null.also {
        Log.w(TAG, "cannot find asset $filename ($source)")
    }
}

