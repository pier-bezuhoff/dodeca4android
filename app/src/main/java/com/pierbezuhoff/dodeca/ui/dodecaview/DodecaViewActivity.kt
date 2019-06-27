package com.pierbezuhoff.dodeca.ui.dodecaview

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
import com.pierbezuhoff.dodeca.databinding.ActivityDodecaViewBinding
import com.pierbezuhoff.dodeca.models.DduFileRepository
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.MainViewModel
import com.pierbezuhoff.dodeca.ui.dduchooser.DduChooserActivity
import com.pierbezuhoff.dodeca.ui.help.HelpActivity
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManagerFactory
import com.pierbezuhoff.dodeca.ui.settings.SettingsActivity
import com.pierbezuhoff.dodeca.utils.FileName
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.copyStream
import com.pierbezuhoff.dodeca.utils.dduDir
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

@RuntimePermissions
class DodecaViewActivity : AppCompatActivity()
    , ChooseColorDialog.ChooseColorListener
    {
        private val optionsManager by lazy {
            OptionsManager(defaultSharedPreferences)
        }
        private val factory by lazy {
            DodecaAndroidViewModelWithOptionsManagerFactory(application, optionsManager)
        }
        private val viewModel by lazy {
            ViewModelProviders.of(this, factory).get(DodecaViewModel::class.java)
        }
        private val shared_mainViewModel = TODO() as MainViewModel
        private val dir: File get() = shared_mainViewModel.currentDir
        private val dduFileRepository =
            DduFileRepository.get(this)

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
            viewModel.viewModelScope.launch {
                if (!handleLaunchFromImplicitIntent())
                    viewModel.loadInitialDdu()
            }
            dodeca_view.inheritLifecycleOf(this)
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
            viewModel.showBottomBar()
            when (id) {
                R.id.help_button -> goToActivity(HelpActivity::class.java, HELP_CODE)
                R.id.load_button -> goToActivity(
                    DduChooserActivity::class.java,
                    DDU_CODE,
                    "dir_path" to dir.absolutePath
                )
                R.id.save_button -> saveDdu()
                R.id.play_button -> viewModel.toggleUpdating()
                R.id.next_step_button -> viewModel.requestOneStep()
                R.id.trace_button -> viewModel.toggleDrawTrace()
                R.id.choose_color_button -> {
                    viewModel.getCircleGroup()?.let { circleGroup: CircleGroup ->
                        temporaryPause()
                        ChooseColorDialog(
                            this,
                            chooseColorListener = this,
                            circleGroup = circleGroup
                        ).build().show()
                    }
                }
                R.id.clear_button -> viewModel.requestClear()
                R.id.autocenter_button -> viewModel.requestAutocenter()
                R.id.settings_button -> goToActivity(SettingsActivity::class.java, APPLY_SETTINGS_CODE)
            }
        }

        private fun <T : AppCompatActivity> goToActivity(cls: Class<T>, resultCode: Int, vararg extraArgs: Pair<String, String>) {
            viewModel.cancelBottomBarHidingJob()
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
                viewModel.requestSaveDdu()
            } else {
                temporaryPause() // BUG: timer job cancellation does not work
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
                    } finally { resumeAfterTemporaryPause() }
                }
                cancelButton { resumeAfterTemporaryPause() }
            }

        private fun temporaryPause() {
            viewModel.pause()
            viewModel.cancelBottomBarHidingJob()
        }

        private fun resumeAfterTemporaryPause() {
            viewModel.resume()
            viewModel.showBottomBar()
        }

        override fun onChooseColorClosed() {
            resumeAfterTemporaryPause()
            viewModel.requestUpdateOnce()
        }

        override fun onResume() {
            super.onResume()
            viewModel.resume()
            viewModel.restartBottomBarHidingJobIfShown()
        }

        override fun onPause() {
            super.onPause()
            viewModel.pause()
            viewModel.cancelBottomBarHidingJob()
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            when (requestCode) {
                DDU_CODE -> {
                    if (resultCode == Activity.RESULT_OK) {
                        data?.getStringExtra("dir_path")?.let { dirPath ->
                            shared_mainViewModel.updateDir(File(dirPath))
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
            viewModel.showBottomBar()
        }

        private fun applyInstantSettings(
            revertCurrentDdu: Boolean = false,
            revertAllDdus: Boolean = false,
            discardAllPreviews: Boolean = false
        ) {
            viewModel.viewModelScope.launch {
                if (revertCurrentDdu) revertCurrentDdu()
                if (revertAllDdus) revertAllDdus()
                if (discardAllPreviews) discardAllPreviews()
            }
        }

        private suspend fun revertCurrentDdu() {
            viewModel.getDduFile()?.let { file: File ->
                shared_mainViewModel.extractDduFrom(file.filename, overwrite = true)
                viewModel.loadDduFrom(file)
            }
        }

        private suspend fun revertAllDdus() {
            shared_mainViewModel.extractDdusFromAssets(overwrite = true)
            viewModel.getDduFile()?.let { file: File ->
                viewModel.loadDduFrom(file)
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
            viewModel.maybeAutosave()
        }

        @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        fun readFile(file: File) {
            viewModel.viewModelScope.launch {
                viewModel.loadDduFrom(file)
            }
        }

        @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        fun readUri(uri: Uri) {
            if (!haveWriteExternalStoragePermission()) {
                Log.i(TAG, "permission WRITE_EXTERNAL_STORAGE not granted yet!")
            }
            val name: Filename = File(uri.path).filename
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val targetFile = withUniquePostfix(dduDir/name)
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
