package com.pierbezuhoff.dodeca.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.Options
import com.pierbezuhoff.dodeca.data.Shape
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.databinding.ActivityMainBinding
import com.pierbezuhoff.dodeca.models.DduFileRepository
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.dduchooser.DduChooserActivity
import com.pierbezuhoff.dodeca.ui.dodeca.ChooseColorDialog
import com.pierbezuhoff.dodeca.ui.dodeca.DodecaViewModel
import com.pierbezuhoff.dodeca.ui.help.HelpActivity
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManagerFactory
import com.pierbezuhoff.dodeca.ui.settings.SettingsActivity
import com.pierbezuhoff.dodeca.utils.FileName
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.copyStream
import com.pierbezuhoff.dodeca.utils.dduDir
import com.pierbezuhoff.dodeca.utils.fileName
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.withUniquePostfix
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.toolbar1.*
import kotlinx.android.synthetic.main.toolbar2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.anko.alert
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.customView
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.editText
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

// ISSUE: add progress [ddu] while loading new ddu (for now it continues last one)
// ISSUE: what if go to choose ddus before extracting 'em all: add progress dialog!
// FIX: temporaryPause
@RuntimePermissions
class MainActivity : AppCompatActivity()
    , ChooseColorDialog.ChooseColorListener
{
    private val optionsManager by lazy {
        OptionsManager(defaultSharedPreferences)
    }
    private val factory by lazy {
        DodecaAndroidViewModelWithOptionsManagerFactory(application, optionsManager)
    }
    private val mainViewModel by lazy {
        ViewModelProviders.of(this, factory).get(MainViewModel::class.java)
    }
    private val dodecaViewModel by lazy {
        ViewModelProviders.of(this, factory).get(DodecaViewModel::class.java)
    }
    private val dir: File get() = mainViewModel.currentDir
    private val dduFileRepository =
        DduFileRepository.get(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: migrate to OptionsViewModel
        Options(resources).init() // init options.* and values.*
        setupWindow()
        val binding: ActivityMainBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.mainViewModel = mainViewModel
        binding.dodecaViewModel = dodecaViewModel
        setSupportActionBar(bar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        setupToolbar()
        mainViewModel.showBottomBar()
        mainViewModel.viewModelScope.launch {
            // MAYBE: show progress dialog/ddu
            if (mainViewModel.shouldUpgrade()) {
                mainViewModel.doUpgrade()
            }
            if (!handleLaunchFromImplicitIntent())
                dodecaViewModel.loadInitialDdu()
        }
        dodeca_view.inheritLifecycle(this)
    }

    private fun setupWindow() {
        window.decorView.apply {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            setOnSystemUiVisibilityChangeListener {
                if ((it and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                    systemUiVisibility =
                        IMMERSIVE_UI_VISIBILITY
            }
        }
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

    private fun handleLaunchFromImplicitIntent(): Boolean {
        if (intent.action == Intent.ACTION_VIEW && (intent.type == null ||
                intent.type?.endsWith("ddu", ignoreCase = true) == true ||
                intent.data?.path?.endsWith(".ddu", ignoreCase = true) == true)
        ) {
            intent.data?.let { readUriWithPermissionCheck(it) }
            return true
        }
        return false
    }

    private fun onToolbarItemClick(id: Int) {
        mainViewModel.showBottomBar()
        when (id) {
            R.id.help_button -> goToActivity(HelpActivity::class.java, HELP_CODE)
            R.id.load_button -> goToActivity(
                DduChooserActivity::class.java,
                DDU_CODE,
                "dir_path" to dir.absolutePath
            )
            R.id.save_button -> saveDdu()
            R.id.play_button -> dodecaViewModel.toggleUpdating()
            R.id.next_step_button -> dodecaViewModel.requestOneStep()
            R.id.trace_button -> dodecaViewModel.toggleDrawTrace()
            R.id.choose_color_button -> {
                dodecaViewModel.getCircleGroup()?.let { circleGroup: CircleGroup ->
                    temporaryPause() // BUG: timer job cancellation does not work
                    ChooseColorDialog(
                        this,
                        chooseColorListener = this,
                        circleGroup = circleGroup
                    ).build().show()
                }
            }
            R.id.clear_button -> dodecaViewModel.requestClear()
            R.id.autocenter_button -> dodecaViewModel.requestAutocenter()
            R.id.settings_button -> goToActivity(SettingsActivity::class.java, APPLY_SETTINGS_CODE)
        }
    }

    private fun <T : AppCompatActivity> goToActivity(cls: Class<T>, resultCode: Int, vararg extraArgs: Pair<String, String>) {
        mainViewModel.cancelBottomBarHidingJob()
        val intent = Intent(this, cls)
        for ((key, arg) in extraArgs)
            intent.putExtra(key, arg)
        startActivityForResult(
            intent,
            resultCode
        )
    }

    private fun saveDdu() {
        if (!values.saveAs) {
            dodecaViewModel.requestSaveDdu()
        } else {
            temporaryPause() // BUG: timer job cancellation does not work
            buildSaveAsDialog().show()
        }
    }

    private fun buildSaveAsDialog() =
        alert(R.string.save_as_message) {
            val initialFileName: FileName = dodecaViewModel.getDduFile()?.fileName ?: FileName("")
            lateinit var fileNameEditText: EditText
            customView {
                fileNameEditText = editText(initialFileName.toString())
            }
            positiveButton(R.string.save_as_button_title) {
                // TODO: check if exists, not blank, etc.
                try {
                    val filename = Filename("${fileNameEditText.text}.ddu")
                    val file = filename.toFile(parent = dir)
                    dodecaViewModel.requestSaveDduAt(file)
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally { resumeAfterTemporaryPause() }
            }
            cancelButton { resumeAfterTemporaryPause() }
        }

    private fun temporaryPause() {
        dodecaViewModel.pause()
        mainViewModel.cancelBottomBarHidingJob()
    }

    private fun resumeAfterTemporaryPause() {
        dodecaViewModel.resume()
        mainViewModel.showBottomBar()
    }

    override fun onChooseColorClosed() {
        resumeAfterTemporaryPause()
        dodecaViewModel.requestUpdateOnce()
    }

    override fun onResume() {
        super.onResume()
        dodecaViewModel.resume()
        mainViewModel.restartBottomBarHidingJobIfShown()
    }

    override fun onPause() {
        super.onPause()
        dodecaViewModel.pause()
        mainViewModel.cancelBottomBarHidingJob()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            DDU_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.getStringExtra("dir_path")?.let { dirPath ->
                        mainViewModel.updateDir(File(dirPath))
                    }
                    data?.getStringExtra("path")?.let { path ->
                        readFile(File(path))
                    }
                }
            }
            APPLY_SETTINGS_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    fun have(param: String): Boolean =
                        data?.getBooleanExtra(param, false) == true
                    applyInstantSettings(
                        revertCurrentDdu = have("default_ddu"),
                        revertAllDdus = have("default_ddus"),
                        discardAllPreviews = have("discard_previews")
                    )
                }
                optionsManager.fetchAll()
            }
            HELP_CODE -> Unit
        }
        mainViewModel.showBottomBar()
    }

    private fun applyInstantSettings(
        revertCurrentDdu: Boolean = false,
        revertAllDdus: Boolean = false,
        discardAllPreviews: Boolean = false
    ) {
        mainViewModel.viewModelScope.launch {
            if (revertCurrentDdu) revertCurrentDdu()
            if (revertAllDdus) revertAllDdus()
            if (discardAllPreviews) discardAllPreviews()
        }
    }

    private suspend fun revertCurrentDdu() {
        dodecaViewModel.getDduFile()?.let { file: File ->
            mainViewModel.extractDduFrom(file.filename)
            dodecaViewModel.loadDduFrom(file)
        }
    }

    private suspend fun revertAllDdus() {
        mainViewModel.extractDdusFromAssets(overwrite = true)
        dodecaViewModel.getDduFile()?.let { file: File ->
            dodecaViewModel.loadDduFrom(file)
        }
    }

    private suspend fun discardAllPreviews() {
        dduFileRepository.dropAllPreviews()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        super.onSaveInstanceState(outState, outPersistentState)
        dodecaViewModel.maybeAutosave()
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun readFile(file: File) {
        dodecaViewModel.viewModelScope.launch {
            dodecaViewModel.loadDduFrom(file)
        }
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun readUri(uri: Uri) {
        if (!haveWriteExternalStoragePermission()) {
            Log.i(TAG, "permission WRITE_EXTERNAL_STORAGE not granted yet!")
        }
        val name: Filename = File(uri.path).filename
        dodecaViewModel.viewModelScope.launch(Dispatchers.IO) {
            val targetFile = withUniquePostfix(name.toFile(parent = dduDir))
            // NOTE: when 'file' scheme (namely from ZArchiver) WRITE_EXTERNAL_STORAGE permission is obligatory
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.let {
                copyStream(it, FileOutputStream(targetFile))
                Log.i(TAG, "imported ddu-file \"$name\"")
                // TODO: check if it's possible to show toast from here (IO thread?)
                toast(getString(R.string.imported_ddu_toast, name))
                dodecaViewModel.loadDduFrom(targetFile)
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

    private class ShapeSpinnerAdapter(private val context: Context) : BaseAdapter() {
        private val shapeDrawableResources: Map<Shape, Int> = mapOf(
            Shape.CIRCLE to R.drawable.ic_circle,
            Shape.SQUARE to R.drawable.ic_square,
            Shape.CROSS to R.drawable.ic_cross,
            Shape.VERTICAL_BAR to R.drawable.ic_vertical_bar,
            Shape.HORIZONTAL_BAR to R.drawable.ic_horizontal_bar
        )
        private val shapes: Array<Shape> = Shape.values()
        private class SpinnerViewHolder(val imageView: ImageView)

        override fun getItem(position: Int): Shape = shapes[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getCount(): Int = shapes.size

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val itemView: View = convertView
                ?: context.layoutInflater
                    .inflate(R.layout.shape_spinner_row, parent, false).apply {
                        tag = SpinnerViewHolder(
                            findViewById(R.id.shape_spinner_image)
                        )
                    }
            val shapeDrawableResource: Int = shapeDrawableResources.getValue(shapes[position])
            (itemView.tag as SpinnerViewHolder).imageView.setImageDrawable(
                ContextCompat.getDrawable(context, shapeDrawableResource))
            return itemView
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val LIMITED_VERSION = false
        // requestCode-s for onActivityResult
        private const val DDU_CODE = 1
        private const val APPLY_SETTINGS_CODE = 2
        private const val HELP_CODE = 3
        /** Distraction free mode */
        private const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}

