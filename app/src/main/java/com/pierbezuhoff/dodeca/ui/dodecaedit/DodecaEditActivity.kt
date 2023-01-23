package com.pierbezuhoff.dodeca.ui.dodecaedit

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.databinding.ActivityDodecaEditBinding
import com.pierbezuhoff.dodeca.models.DduFileService
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.dduchooser.DduChooserActivity
import com.pierbezuhoff.dodeca.ui.dodecaview.DodecaViewActivity
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManagerFactory
import com.pierbezuhoff.dodeca.ui.settings.SettingsActivity
import com.pierbezuhoff.dodeca.utils.FileName
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.copyStream
import com.pierbezuhoff.dodeca.utils.div
import com.pierbezuhoff.dodeca.utils.fileName
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.withUniquePostfix
import kotlinx.android.synthetic.main.activity_dodeca_edit.*
import kotlinx.android.synthetic.main.toolbar_edit1.*
import kotlinx.android.synthetic.main.toolbar_edit2.*
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
class DodecaEditActivity : AppCompatActivity()
    , MassEditorDialog.MassEditorListener
    , AdjustAnglesDialog.AdjustAnglesListener
{

    private val optionsManager by lazy {
        OptionsManager(application)
    }
    private val factory by lazy {
        DodecaAndroidViewModelWithOptionsManagerFactory(application, optionsManager)
    }
    private val viewModel by lazy {
        ViewModelProvider(this, factory).get(DodecaEditViewModel::class.java)
    }
    private val dduFileService by lazy {
        DduFileService(applicationContext)
    }
    private val dir: File get() = viewModel.dir

    private val dduResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { onDduResult(it.resultCode, it.data) }
    private val settingsResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { onSettingsResult(it.resultCode, it.data) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        val binding: ActivityDodecaEditBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_dodeca_edit)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        setSupportActionBar(edit_bar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        setupToolbar()
        viewModel.showBottomBar()
        val uri = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra("ddu_uri", Uri::class.java)
        else intent.getParcelableExtra<Uri>("ddu_uri")
        uri?.let { readUriWithPermissionCheck(it) } ?: viewModel.loadInitialDdu()
        dodeca_edit_view.inheritLifecycleOf(this)
        dodeca_edit_view.setLayerType(View.LAYER_TYPE_SOFTWARE, null) // makes dashed lines visible, but maybe(?) slower
        // viewModel.overwriteForceRedraw() // idk
    }

    private fun setupWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.decorView.apply {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            setOnSystemUiVisibilityChangeListener {
                if ((it and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                    systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            }
        }
//        hideSystemBars()
//        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
//            Log.i(TAG, "insets!")
//            if (insets.isVisible(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()))
//                hideSystemBars() // doesnt work properly :(
//            insets
//        }
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        window.decorView.apply {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
        }
    }

    private fun setupToolbar() {
        setOf(toolbar_edit1, toolbar_edit2).forEach { toolbar ->
            toolbar.children.filterIsInstance(ImageButton::class.java).forEach { button ->
                button.setOnClickListener { onToolbarItemClick(it.id) }
            }
        }
    }

    // BUG: no tooltips!
    private fun onToolbarItemClick(id: Int) {
        when (id) {
            R.id.navigate_mode_button -> viewModel.requestEditingMode(EditingMode.NAVIGATE)
            R.id.multiselect_mode_button -> viewModel.requestEditingMode(EditingMode.MULTISELECT)
            R.id.copy_mode_button -> viewModel.requestEditingMode(EditingMode.COPY)
            R.id.new_circle_button -> 5
            R.id.angles_button -> {
                viewModel.getCircleGroup()?.let { cg ->
                    viewModel.pause()
                    AdjustAnglesDialog(
                        this,
                        adjustAnglesListener = this,
                        cg
                    ).build()
                        .show()
                }
            }
            R.id.done_button -> {
                viewModel.requestSaveDdu() // NOTE: saving is async and might get gc-ed away (?!)
                viewModel.restoreForceRedraw()
                val intent = Intent(this, DodecaViewActivity::class.java)
//                viewModel.dduRepresentation.value?.ddu?.file?.let { dduFile ->
//                    intent.putExtra("ddu_uri", Uri.fromFile(dduFile))
//                }
                startActivity(intent)
            }
            R.id.load_button -> goToActivity(
                DduChooserActivity::class.java,
                dduResultLauncher,
                "dir_path" to dir.absolutePath
            )
            R.id.save_button -> saveDdu()
            R.id.play_button -> viewModel.toggleUpdating()
            R.id.show_everything_button -> {
                viewModel.toggleShowEverything()
            }
            R.id.mass_editor_button -> {
                // TODO: pass current selection into editor
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
            R.id.settings_button -> goToActivity(SettingsActivity::class.java, settingsResultLauncher)
        }
    }

    private fun <T : AppCompatActivity> goToActivity(cls: Class<T>, resultLauncher: ActivityResultLauncher<Intent>, vararg extraArgs: Pair<String, String>) {
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

    private fun onDialogClosed() {
        hideSystemBars()
        viewModel.resume()
        viewModel.requestUpdateOnce()
    }

    override fun onMassEditorClosed() =
        onDialogClosed()

    override fun onMassEditorBackgroundChanged() {
        viewModel.dduRepresentation.value?.clearTrace()
    }

    override fun onMassEditorCirclesSelected(circleIndices: List<Int>) {
        onDialogClosed()
        // go into multiselect mode + select ixs
        viewModel.requestEditingMode(EditingMode.MULTISELECT)
        TODO()
    }

    override fun onAdjustAnglesClosed() =
        onDialogClosed()

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
    }

    private fun onSettingsResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            fun received(param: String): Boolean =
                data?.getBooleanExtra(param, false) == true
            viewModel.applyInstantSettings(
                revertCurrentDdu = received("default_ddu"),
                revertAllDdus = received("default_ddus"),
                discardAllPreviews = received("discard_previews"),
                updateCircleGroup = received("update_circlegroup")
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

    override fun onDestroy() {
        viewModel.restoreForceRedraw()
        super.onDestroy()
    }

    companion object {
        const val TAG = "DodecaEditActivity"
        private const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
