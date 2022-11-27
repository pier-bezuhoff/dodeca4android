package com.pierbezuhoff.dodeca.ui.dodecaview

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.drawToBitmap
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.databinding.ActivityDodecaViewBinding
import com.pierbezuhoff.dodeca.models.DduFileService
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.dduchooser.DduChooserActivity
import com.pierbezuhoff.dodeca.ui.dodecaedit.DodecaEditActivity
import com.pierbezuhoff.dodeca.ui.dodecaedit.MassEditorDialog
import com.pierbezuhoff.dodeca.ui.help.HelpActivity
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManagerFactory
import com.pierbezuhoff.dodeca.ui.settings.SettingsActivity
import com.pierbezuhoff.dodeca.utils.FileName
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.copyStream
import com.pierbezuhoff.dodeca.utils.div
import com.pierbezuhoff.dodeca.utils.fileName
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.withUniquePostfix
import kotlinx.android.synthetic.main.activity_dodeca_view.*
import kotlinx.android.synthetic.main.toolbar1.*
import kotlinx.android.synthetic.main.toolbar2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.alert
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.customView
import org.jetbrains.anko.editText
import org.jetbrains.anko.toast
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.OnShowRationale
import permissions.dispatcher.PermissionRequest
import permissions.dispatcher.RuntimePermissions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@RuntimePermissions
class DodecaViewActivity : AppCompatActivity()
    , MassEditorDialog.MassEditorListener
{
    private val optionsManager by lazy {
        OptionsManager(application)
    }
    private val factory by lazy {
        DodecaAndroidViewModelWithOptionsManagerFactory(application, optionsManager)
    }
    private val viewModel by lazy {
        ViewModelProvider(this, factory).get(DodecaViewModel::class.java)
    }
    private val dduFileService by lazy {
        DduFileService(applicationContext)
    }
    private val dir: File get() = viewModel.dir

    private val dduResultLauncher = registerForActivityResult(StartActivityForResult()) { onDduResult(it.resultCode, it.data) }
    private val settingsResultLauncher = registerForActivityResult(StartActivityForResult()) { onSettingsResult(it.resultCode, it.data) }
    private val helpResultLauncher = registerForActivityResult(StartActivityForResult()) { viewModel.showBottomBar() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        val binding: ActivityDodecaViewBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_dodeca_view)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        setSupportActionBar(bar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        setupToolbar()
        viewModel.showBottomBar()
        // if launched from implicit intent (uri passed from [MainActivity]):
        val uri = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra("ddu_uri", Uri::class.java)
        else intent.getParcelableExtra<Uri>("ddu_uri")
        uri?.let { readUriWithPermissionCheck(it) } ?: viewModel.loadInitialDdu()
        dodeca_view.inheritLifecycleOf(this)
    }

    private fun setupWindow() {
//        hideSystemBars()
//        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
//            if (insets.isVisible(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()))
//                hideSystemBars() // doesnt work sadly
//            insets
//        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.decorView.apply {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            setOnSystemUiVisibilityChangeListener {
                if ((it and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                    systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            }
        }
    }

    private fun hideSystemBars() { // yokunai
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupToolbar() {
        setOf(toolbar1, toolbar2).forEach { toolbar ->
            toolbar.children.filterIsInstance(ImageButton::class.java).forEach { button ->
                button.setOnClickListener { onToolbarItemClick(it.id) }
            }
        }
        // BUG: after BOTTOM_BAR_HIDE_DELAY selection does not work!
        with(shape_spinner) {
            adapter = ShapeSpinnerAdapter(context)
        }
    }

    private fun onToolbarItemClick(id: Int) {
        viewModel.showBottomBar()
        when (id) {
            R.id.help_button -> goToActivity(HelpActivity::class.java, helpResultLauncher)
            R.id.load_button -> goToActivity(
                DduChooserActivity::class.java,
                dduResultLauncher,
                "dir_path" to dir.absolutePath
            )
            R.id.save_button -> saveDdu()
            R.id.play_button -> viewModel.toggleUpdating()
            R.id.next_step_button -> viewModel.requestOneStep()
            R.id.trace_button -> viewModel.toggleDrawTrace()
            R.id.clear_button -> viewModel.requestClear()
            R.id.edit_button -> {
                viewModel.requestSaveDdu() // NOTE: saving is async and might get gc-ed away (?!)
                val intent = Intent(this, DodecaEditActivity::class.java)
                startActivity(intent)
            }
            R.id.mass_editor_button -> {
                viewModel.dduRepresentation.value?.let { dduR ->
                    viewModel.pause()
                    MassEditorDialog(
                        this,
                        massEditorListener = this,
                        ddu = dduR.ddu,
                        circleGroup = dduR.circleGroup
                    ).build()
                        .show()
                }
            }
            R.id.screenshot_button -> saveScreenshotWithPermissionCheck()
            R.id.autocenter_button -> viewModel.requestAutocenter()
            R.id.restart_button -> {
                viewModel.reloadDdu()
                // reload ddu
                // hide bottom bar
                // pause 3s
                // start
            }
            R.id.settings_button -> goToActivity(SettingsActivity::class.java, settingsResultLauncher)
        }
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun saveScreenshot() {
        val screenshot: Bitmap? =
            if (false) dodeca_view.drawToBitmap() // ??? idr
            else viewModel.takeFullScreenshot()
        screenshot?.let {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val appName = getString(R.string.app_name)
            val dir = picturesDir/Filename(appName)
            if (!dir.exists())
                dir.mkdir()
            val name = viewModel.dduRepresentation.value?.ddu?.file?.nameWithoutExtension ?: "untitled-ddu"
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val file = mkScreenshotFile(dir, name)
                file.outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                // add img to gallery, TODO: test it
//                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply { data = Uri.fromFile(file) })
                MediaScannerConnection.scanFile(applicationContext, arrayOf(file.toString()), null, null)
                withContext(Dispatchers.Main) {
                    toast(getString(R.string.screenshot_saved_toast, file.name, "${picturesDir.name}/$appName"))
                }
            }
        }
    }

    private fun mkScreenshotFile(dir: File, name: String): File {
        // TODO: add try catch block cuz crawler somehow caused an IOError no file found
        val similarFiles: Array<out File>? = dir
            .listFiles { file -> file.nameWithoutExtension.startsWith(name) }
        val similar = similarFiles?.map { it.nameWithoutExtension } ?: emptyList()
        var i = 0
        val names = sequenceOf(name) + generateSequence {
            i++
            return@generateSequence "$name-$i"
        }
        val newName = (names - similar.toSet()).first()
        val file = dir/Filename("$newName.png")
        file.createNewFile()
        return file
    }

    private fun <T : AppCompatActivity> goToActivity(cls: Class<T>, resultLauncher: ActivityResultLauncher<Intent>, vararg extraArgs: Pair<String, String>) {
        viewModel.hideBottomBar()
        val intent = Intent(this, cls)
        for ((key, arg) in extraArgs)
            intent.putExtra(key, arg)
        resultLauncher.launch(intent)
    }

    private fun saveDdu() {
        if (!optionsManager.values.saveAs) {
            viewModel.requestSaveDdu()
        } else {
            viewModel.pause()
            buildSaveAsDialog().show()
        }
    }

    private fun buildSaveAsDialog() =
        alert(R.string.save_as_message) {
            val initialFileName: FileName = viewModel.getDduFile()?.fileName ?: FileName("")
            lateinit var fileNameEditText: EditText
            customView {
                fileNameEditText = editText(initialFileName.toString())
            }
            positiveButton(R.string.save_as_button_title) {
                // MAYBE: check if exists, not blank, etc.
                try {
                    val filename = Filename("${fileNameEditText.text}.ddu")
                    val file: File = dir/filename
                    viewModel.requestSaveDduAt(file)
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally { viewModel.resume() }
            }
            cancelButton { viewModel.resume() }
        }

    override fun onMassEditorClosed() {
        viewModel.resume()
        viewModel.requestUpdateOnce()
    }

    override fun onMassEditorBackgroundChanged() {
        viewModel.dduRepresentation.value?.clearTrace()
    }

    override fun onMassEditorCirclesSelected(circleIndices: List<Int>) {
        onMassEditorClosed()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.pause()
    }

    private fun onDduResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            data?.getStringExtra("ddu_path")?.let { path ->
                readFile(File(path))
            }
        }
        viewModel.showBottomBar()
    }

    private fun onSettingsResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            fun have(param: String): Boolean =
                data?.getBooleanExtra(param, false) == true
            viewModel.applyInstantSettings(
                revertCurrentDdu = have("default_ddu"),
                revertAllDdus = have("default_ddus"),
                discardAllPreviews = have("discard_previews")
            )
        }
        optionsManager.fetchAll()
        viewModel.showBottomBar()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        viewModel.maybeAutosave()
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun readFile(file: File) {
        viewModel.viewModelScope.launch {
            viewModel.loadDduFrom(file)
        }
    }

    // TODO: merge with DduFileService.importByUri
    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun readUri(uri: Uri) {
        if (!haveWriteExternalStoragePermission()) {
            Log.i(TAG, "permission WRITE_EXTERNAL_STORAGE not granted yet!")
        }
        uri.path?.let { path ->
            val name: Filename = File(path).filename
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val targetFile = withUniquePostfix(dduFileService.dduDir/name)
                // NOTE: when 'file' scheme (namely from ZArchiver) WRITE_EXTERNAL_STORAGE permission is obligatory
                val inputStream = contentResolver.openInputStream(uri)
                inputStream?.let {
                    copyStream(it, FileOutputStream(targetFile))
                    Log.i(TAG, "imported ddu-file \"$name\"")
                    withContext(Dispatchers.Main) {
                        toast(getString(R.string.imported_ddu_toast, name))
                    }
                    viewModel.loadDduFrom(targetFile)
                }
            }
        }
    }

    private fun haveWriteExternalStoragePermission(): Boolean {
        val permissionStatus = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        return permissionStatus == PackageManager.PERMISSION_GRANTED
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

    companion object {
        private const val TAG = "DodecaViewActivity"
        /** Distraction free mode (deprecated) */
        private const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        private const val SCREENSHOTS_DIR_NAME = "DodecaMeditation"
    }
}

