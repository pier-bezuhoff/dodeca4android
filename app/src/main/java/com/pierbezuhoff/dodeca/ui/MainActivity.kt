package com.pierbezuhoff.dodeca.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import com.pierbezuhoff.dodeca.BuildConfig
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.Options
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.databinding.ActivityMainBinding
import com.pierbezuhoff.dodeca.models.DodecaViewModel
import com.pierbezuhoff.dodeca.models.MainViewModel
import com.pierbezuhoff.dodeca.models.SharedPreferencesModel
import com.pierbezuhoff.dodeca.utils.DB
import com.pierbezuhoff.dodeca.utils.DduFileDao
import com.pierbezuhoff.dodeca.utils.DduFileDatabase
import com.pierbezuhoff.dodeca.utils.FileName
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.dduDir
import com.pierbezuhoff.dodeca.utils.extract1Ddu
import com.pierbezuhoff.dodeca.utils.withUniquePostfix
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.toolbar1.*
import kotlinx.android.synthetic.main.toolbar2.*
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
class MainActivity : AppCompatActivity(), ChooseColorDialog.ChooseColorListener, DodecaGestureDetector.SingleTapListener {
    private val model by lazy {
        ViewModelProviders.of(this).get(MainViewModel::class.java)
    }
    private val dodecaViewModel by lazy {
        ViewModelProviders.of(this).get(DodecaViewModel::class.java)
    }
    private val sharedPreferencesModel by lazy {
        SharedPreferencesModel(defaultSharedPreferences)
    }
    private val dir: File get() = model.dir.value ?: dduDir
    private val dduFileDao: DduFileDao by lazy { DB.dduFileDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Options(resources).init() // init options.* and values.*
        DduFileDatabase.init(this) /// the faster the better
        sharedPreferencesModel.fetch(options.versionCode)
        checkUpgrade()
        window.decorView.apply {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            setOnSystemUiVisibilityChangeListener {
                if ((it and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                    systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            }
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.model = model
        binding.dodecaViewModel = dodecaViewModel
        binding.sharedPreferencesModel = sharedPreferencesModel
        dodecaViewModel.sharedPreferencesModel = sharedPreferencesModel
        model.setDirOnce(dduDir)
        setSupportActionBar(bar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        // listen single tap, scroll and scale gestures
        DodecaGestureDetector(this).let {
            it.registerSingleTapListener(this)
            dodecaViewModel.registerGestureDetector(it)
        }
        // handling launch from implicit intent
        Log.i(TAG, "Dodeca started${if (intent.action == Intent.ACTION_VIEW) " from implicit intent: ${intent.data?.path ?: "-"}" else ""}")
        if (intent.action == Intent.ACTION_VIEW && (intent.type == null ||
                intent.type?.endsWith("ddu", ignoreCase = true) == true ||
                intent.data?.path?.endsWith(".ddu", ignoreCase = true) == true)) {
            intent.data?.let { readUriWithPermissionCheck(it) }
        }
        setupToolbar()
        model.showBottomBar()
    }

    private fun checkUpgrade() {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val oldVersionCode = values.versionCode
        if (oldVersionCode != currentVersionCode) {
            val upgrading: Boolean = oldVersionCode < currentVersionCode
            val upgradingOrDegrading: String = if (upgrading) "Upgrading" else "Degrading"
            val currentVersionName: String = BuildConfig.VERSION_NAME
            val versionCodeChange: String = "$oldVersionCode -> $currentVersionCode"
            Log.i(TAG,"$upgradingOrDegrading to $currentVersionName ($versionCodeChange)")
            sharedPreferencesModel.set(options.versionCode, currentVersionCode)
            onUpgrade()
        }
    }

    override fun onSingleTap(e: MotionEvent?) = model.toggleBottomBar()

    private fun onUpgrade() {
        // extracting assets
        try {
            if (!dduDir.exists()) {
                Log.i(TAG, "Extracting all assets into $dduDir")
                dduDir.mkdir()
                extractDduFromAssets()
            } else {
                // TODO: check it
                // try to export new ddus
                Log.i(TAG, "Adding new ddu assets into $dduDir")
                val existedDdus = dduDir.listFiles().map { it.name }.toSet()
                assets.list(getString(R.string.ddu_asset_dir))?.filter { it !in existedDdus }?.forEach { name ->
                    extract1Ddu(name, dduDir)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun setupToolbar() {
        setOf(toolbar1, toolbar2).forEach { toolbar ->
            toolbar.children.filterIsInstance(ImageButton::class.java).forEach { button ->
                button.setOnClickListener { onToolbarItemClick(it.id) }
            }
        }
        // ISSUE: on spinner dialog: stop bottom bar timer, pause dodecaView
        // BUG: after BOTTOM_BAR_HIDE_DELAY selection does not work!
        with(shape_spinner) {
            adapter = ShapeSpinnerAdapter(context)
            setSelection(0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) { model.showBottomBar() }
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    dodecaViewModel.shape.value = Shapes.indexOrFirst(id.toInt())
                    model.showBottomBar()
                }
            }
        }
    }

    private fun onToolbarItemClick(id: Int) {
        model.showBottomBar()
        when (id) {
            R.id.help_button -> {
                val intent = Intent(this, HelpActivity::class.java)
                startActivityForResult(intent, HELP_CODE)
            }
            R.id.load_button -> {
                val intent = Intent(this, DduChooserActivity::class.java)
                intent.putExtra("dirPath", dir.absolutePath)
                startActivityForResult(intent, DDU_CODE)
            }
            R.id.save_button -> saveDDU()
            R.id.play_button -> dodecaViewModel.toggleUpdating()
            R.id.next_step_button -> dodecaViewModel.requestOneStep()
            R.id.trace_button -> dodecaViewModel.toggleDrawTrace()
            R.id.choose_color_button -> {
                dodecaViewModel.circleGroup.value?.let { circleGroup ->
                    dodecaViewModel.pause()
                    model.stopBottomBarHideTimer() // will be restarted in onChooseColorClosed()
                    ChooseColorDialog(this, circleGroup).build().show()
                }
            }
            R.id.clear_button -> dodecaViewModel.requestClear()
            R.id.autocenter_button -> dodecaViewModel.requestAutocenter()
            R.id.settings_button -> {
                startActivityForResult(
                    Intent(this@MainActivity, SettingsActivity::class.java),
                    APPLY_SETTINGS_CODE
                )
            }
        }
    }

    private fun saveDDU() {
        if (!values.saveAs) {
            dodecaViewModel.requestSaveDduAt()
        } else {
            buildSaveAsDialog().show()
        }
    }

    private fun buildSaveAsDialog() =
        alert(R.string.save_as_message) {
            val initialFileName: FileName = dodecaViewModel.ddu.value?.file?.nameWithoutExtension ?: ""
            lateinit var fileNameEditText: EditText
            customView {
                fileNameEditText = editText(initialFileName)
            }
            positiveButton(R.string.save_as_button_title) {
                // TODO: check if exists, not blank, etc.
                try {
                    val filename: Filename = "${fileNameEditText.text}.ddu"
                    val file = File(dir, filename)
                    dodecaViewModel.requestSaveDduAt(file)
                    Log.i(TAG, file.path)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            cancelButton {  }
        }

    override fun onChooseColorClosed() {
        dodecaViewModel.resume()
        model.showBottomBar()
    }

    override fun onResume() {
        super.onResume()
        dodecaViewModel.resume()
    }

    override fun onPause() {
        super.onPause()
        dodecaViewModel.pause()
        if (values.autosave && dodecaViewModel.ddu.value?.file != null)
            dodecaViewModel.requestSaveDduAt()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            DDU_CODE ->
                if (resultCode == Activity.RESULT_OK) {
                    data?.getStringExtra("dirPath")?.let { newDir ->
                        model.changeDir(File(newDir)) }
                    data?.getStringExtra("path")?.let {
                        readPath(it)
                    }
                }
            APPLY_SETTINGS_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    val having =  { param: String, action: () -> Unit ->
                        if (data?.getBooleanExtra(param, false) == true)
                            action()
                    }
                    having("default_ddu") {
                        dodecaViewModel.ddu.value?.file?.let { file ->
                            extract1Ddu(file.name)
                            dodecaViewModel.loadDdu(Ddu.fromFile(file))
                        }
                    }
                    having("default_ddus") {
                        extractDduFromAssets(overwrite = true)
                        dodecaViewModel.ddu.value?.file?.let { file ->
                            dodecaViewModel.loadDdu(Ddu.fromFile(file))
                        }
                    }
                    having("discard_previews") {
                        dduFileDao.getAll().forEach { dduFileDao.update(it.apply { preview = null }) }
                    }
                }
                sharedPreferencesModel.fetchAll()
            }
            HELP_CODE -> Unit
        }
        model.showBottomBar()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        model.sendOnDestroy()
        super.onDestroy()
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    fun readPath(path: String) {
        Log.i(TAG, "reading ddu from path $path...")
        try {
            val file = File(path)
            dodecaViewModel.loadDdu(Ddu.fromFile(file))
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.bad_ddu_format_toast) + " $path")
        }
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun readUri(uri: Uri) {
        if (!haveWriteExternalStoragePermission()) {
            Log.i(TAG, "permission WRITE_EXTERNAL_STORAGE not granted yet!")
        }
        Log.i(TAG, "reading ddu from uri $uri")
        try {
            val name = File(uri.path).name
            val targetFile = withUniquePostfix(File(dduDir, name))
            // NOTE: when 'file' scheme (namely from ZArchiver) WRITE_EXTERNAL_STORAGE permission is obligatory
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.let {
                it.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                }
                Log.i(TAG, "imported ddu \"$name\"")
                toast(getString(R.string.imported_ddu_toast) + " $name")
                val ddu: Ddu = Ddu.fromFile(targetFile)
                dodecaViewModel.loadDdu(ddu)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            toast(getString(R.string.bad_ddu_format_toast) + " ${uri.path}")
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

    private fun extractDduFromAssets(overwrite: Boolean = false) {
        val targetDir = dduDir
        assets.list(getString(R.string.ddu_asset_dir))?.forEach {name ->
            extract1Ddu(name, targetDir, overwrite)
        }
    }

    private fun extract1Ddu(filename: Filename, dir: File = dduDir, overwrite: Boolean = false) =
        extract1Ddu(filename, dir, dduFileDao, TAG, overwrite)

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
        const val DDU_CODE = 1
        const val APPLY_SETTINGS_CODE = 2
        const val HELP_CODE = 3
        // fullscreen, but with bottom navigation
        const val FULLSCREEN_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_FULLSCREEN
        // distraction free
        const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}

