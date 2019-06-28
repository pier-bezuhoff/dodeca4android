package com.pierbezuhoff.dodeca.ui.dduchooser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.databinding.ActivityDduChooserBinding
import com.pierbezuhoff.dodeca.models.DduFileRepository
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManagerFactory
import com.pierbezuhoff.dodeca.utils.FileName
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.copyDirectory
import com.pierbezuhoff.dodeca.utils.copyFile
import com.pierbezuhoff.dodeca.utils.copyStream
import com.pierbezuhoff.dodeca.utils.dduDir
import com.pierbezuhoff.dodeca.utils.div
import com.pierbezuhoff.dodeca.utils.extractDduFrom
import com.pierbezuhoff.dodeca.utils.fileName
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.getDisplayName
import com.pierbezuhoff.dodeca.utils.isDdu
import com.pierbezuhoff.dodeca.utils.withUniquePostfix
import kotlinx.android.synthetic.main.activity_ddu_chooser.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import org.jetbrains.anko.alert
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.customView
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.editText
import org.jetbrains.anko.toast
import org.jetbrains.anko.yesButton
import java.io.File

class DduChooserActivity : AppCompatActivity()
    , ContextMenuManager
    , DirAdapter.DirChangeListener
    , DduFileAdapter.FileChooser
{
    private val optionsManager by lazy {
        OptionsManager(defaultSharedPreferences)
    }
    private val factory by lazy {
        DodecaAndroidViewModelWithOptionsManagerFactory(application, optionsManager)
    }
    private val viewModel by lazy {
        ViewModelProviders.of(this, factory).get(DduChooserViewModel::class.java)
    }
    private val dduFileRepository =
        DduFileRepository.get(this)
    private val dir: File get() = viewModel.currentDir.value ?: dduDir
    private var createdContextMenu: ContextMenuSource? = null
    private var requestedDduFile: File? = null
    private var requestedDduDir: File? = null
    private lateinit var dirDeltaList: DeltaList<File>
    private lateinit var dduFileDeltaList: DeltaList<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityDduChooserBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_ddu_chooser)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        setInitialDir()
        initDirRecyclerView()
        initDduRecyclerView()
    }

    private fun setInitialDir() {
        // MAYBE: just get viewModel.currentDir (from recentDdu)
        val initialDir = intent.getStringExtra("dir_path")
            ?.let { dirPath ->
                val newDir = File(dirPath)
                if (newDir.exists()) newDir else null
            } ?: dduDir
        viewModel.setInitialDir(initialDir)
    }

    private fun initDirRecyclerView() {
        val adapter = DirAdapter(viewModel.dirs)
        dirDeltaList = DeltaList(viewModel.dirs, adapter)
        dir_recycler_view.adapter = adapter
        dir_recycler_view.layoutManager = LinearLayoutManager(applicationContext)
        dir_recycler_view.itemAnimator = DefaultItemAnimator()
        adapter.dirChangeSubscription.subscribeFrom(this)
        adapter.contextMenuSubscription.subscribeFrom(this)
    }

    private fun initDduRecyclerView() {
        val adapter = DduFileAdapter(viewModel.files)
        dduFileDeltaList = DeltaList(viewModel.files, adapter)
        ddu_recycler_view.adapter = adapter
        // NOTE: colors and thickness of dividers are set from styles.xml
        ddu_recycler_view.addItemDecoration(DividerItemDecoration(applicationContext, LinearLayoutManager.VERTICAL))
        ddu_recycler_view.addItemDecoration(DividerItemDecoration(applicationContext, LinearLayoutManager.HORIZONTAL))
        ddu_recycler_view.itemAnimator = DefaultItemAnimator()
        adapter.fileChooserSubscription.subscribeFrom(this)
        adapter.contextMenuSubscription.subscribeFrom(this)
        adapter.previewSupplierSubscription.subscribeFrom(viewModel)
        adapter.inheritLifecycleOf(this)
        val lastFile = dduDir/values.recentDdu
        adapter.findPositionOf(lastFile)?.let { position: Int ->
            // NOTE: works bad when position is in the end of adapter
            //  also jumping slightly when scrolling upward
            ddu_recycler_view.scrollToPosition(position)
        }
    }

    override fun onDirChanged(dir: File) {
        viewModel.goToDir(dir)
        dirDeltaList.updateAll()
        dduFileDeltaList.updateAll()
    }

    override fun chooseFile(file: File) {
        Log.i(TAG, "File \"${file.absolutePath}\" chosen")
        val intent = Intent()
        intent.putExtra("ddu_path", file.absolutePath)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun registerViewForContextMenu(view: View) {
        registerForContextMenu(view)
    }

    override fun createMenu(menuRes: Int, menu: Menu, contextMenuSource: ContextMenuSource) {
        menuInflater.inflate(menuRes, menu)
        createdContextMenu = contextMenuSource
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.ddu_chooser_appbar, menu)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            menu?.removeItem(R.id.import_dir)
            menu?.removeItem(R.id.export_ddus)
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isSet = true
        when (item.itemId) {
            R.id.to_parent_dir -> navigateToParentDir()
            R.id.import_ddus -> requestImportDdus()
            R.id.export_ddus -> requestExportDduDir()
            R.id.import_dir -> requestImportDduDir()
            R.id.delete_ddus -> deleteAll()
            R.id.toggle_folders -> toggleFolders()
            else -> isSet = false
        }
        return isSet || super.onOptionsItemSelected(item)
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        var isSet1 = true
        var isSet2 = true
        createdContextMenu?.let { source ->
            when (source) {
                is ContextMenuSource.DduFile -> {
                    val file: File = source.file
                    when (item?.itemId) {
                        R.id.ddu_rename -> renameDduFile(file)
                        R.id.ddu_delete -> deleteDduFile(file)
                        R.id.ddu_restore -> restoreDduFile(file)
                        R.id.ddu_duplicate -> duplicateDduFile(file)
                        R.id.ddu_export -> requestExportDduFile(file)
                        R.id.ddu_export_for_dodecalook -> requestExportDduFileForDodecaLook(file)
                        else -> isSet1 = false
                    }
                }
                is ContextMenuSource.Dir -> {
                    val dir: File = source.dir
                    when (item?.itemId) {
                        R.id.dir_delete -> deleteDir(dir)
                        R.id.dir_export -> requestExportDduDir(dir)
                        else -> isSet2 = false
                    }
                }
            }
        }
        return isSet1 || isSet2 || super.onContextItemSelected(item)
    }

    private fun refreshDir() {
        viewModel.goToDir(dir)
    }

    private fun navigateToParentDir() {
        if (dir.absolutePath != dduDir.absolutePath) {
            viewModel.goToDir(dir.parentFile)
            dirDeltaList.updateAll()
            dduFileDeltaList.updateAll()
        }
    }

    private fun requestImportDdus() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, IMPORT_DDUS_REQUEST_CODE)
    }

    private fun importDdus(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            toast(getString(R.string.ddus_importing_toast))
            viewModel.loadingDdus {
                uris.forEach { uri ->
                    DocumentFile.fromSingleUri(this@DduChooserActivity, uri)?.let { file ->
                        val displayName: Filename? by lazy {
                            contentResolver.getDisplayName(uri)?.toString()
                                ?.let {
                                    Filename(if ('.' !in it) "$it.ddu" else it)
                                }
                        }
                        val filename: Filename =
                            file.filename ?: displayName ?: DEFAULT_DDU_FILENAME
                        val target0: File = dir/filename
                        val target: File =
                            if (target0.exists()) withUniquePostfix(target0)
                            else target0
                        Log.i(TAG, "importing file \"${target.name}\" from \"${file.uri}\"")
                        contentResolver.copyFile(file, target)
                    }
                }
            }
            refreshDir()
            withContext(Dispatchers.Main) {
                dduFileDeltaList.updateAll()
                toast(getString(R.string.ddus_imported_toast))
            }
        }
    }

    private fun requestExportDduDir(targetDir: File = dir) {
        requestedDduDir = targetDir
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, EXPORT_DIR_REQUEST_CODE)
        }
    }

    private fun exportDduDir(uri: Uri) {
        requestedDduDir?.let { dir ->
            Log.i(TAG, "exporting currentDir \"${dir.name}\"")
            toast(getString(R.string.dir_exporting_toast, dir.name))
            lifecycleScope.launch(Dispatchers.IO) {
                viewModel.loadingDdus {
                    DocumentFile.fromTreeUri(this@DduChooserActivity, uri)
                        ?.let { targetDir ->
                            exportDduDocumentFile(dir, targetDir)
                        }
                }
                withContext(Dispatchers.Main) {
                    toast(getString(R.string.dir_exported_toast, dir.name))
                }
            }
            requestedDduDir = null
        }
    }

    private suspend fun exportDduDocumentFile(source: File, targetDir: DocumentFile) {
        withContext(Dispatchers.IO) {
            when {
                source.isDirectory -> targetDir.createDirectory(source.name)?.let { newDir ->
                    source.listFiles().forEach { exportDduDocumentFile(it, newDir) }
                }
                source.isDdu -> targetDir.createFile("*/*", source.name)?.let { newFile ->
                    contentResolver.openOutputStream(newFile.uri)?.let { outputStream ->
                        copyStream(source.inputStream(), outputStream)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun requestImportDduDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, IMPORT_DIR_REQUEST_CODE)
        } // NOTE: Android < 5 has no Intent.ACTION_OPEN_DOCUMENT_TREE
    }

    private fun importDduDir(source: DocumentFile) {
        Log.i(TAG, "importing currentDir \"${source.name}\"")
        toast(getString(R.string.dir_importing_toast, source.name))
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadingDdus {
                val target = File(dir, source.name)
                contentResolver.copyDirectory(source, target)
                withContext(Dispatchers.Main) {
                    dirDeltaList.add(target)
                    toast(getString(R.string.dir_imported_toast, dir.name))
                }
            }
        }
    }

    private fun deleteAll() {
        alert(
            R.string.delete_all_alert_message,
            R.string.delete_all_alert_title
        ) {
            yesButton {
                lifecycleScope.launch(Dispatchers.IO) {
                    viewModel.loadingDdus {
                        dir.walkTopDown().iterator().forEach {
                            if (it.isDdu) dduFileRepository.deleteIfExists(it.filename)
                        }
                        FileUtils.cleanDirectory(dir)
                        refreshDir()
                    }
                    withContext(Dispatchers.Main) {
                        dduFileDeltaList.updateAll()
                        dirDeltaList.updateAll()
                    }
                }
            }
            cancelButton { }
        }.show()
    }

    private fun toggleFolders() {
        dir_recycler_view.visibility = when(dir_recycler_view.visibility) {
            View.GONE -> View.VISIBLE
            View.VISIBLE -> View.GONE
            else -> dir_recycler_view.visibility
        }
    }

    private fun renameDduFile(file: File) {
        lifecycleScope.launch {
            val name: FileName = file.fileName
            var input: EditText? = null
            val originalFilename: Filename? = dduFileRepository.getOriginalFilename(file.filename)
            val appendix =
                if (originalFilename != null && originalFilename != file.filename)
                    " " + getString(R.string.rename_dialog_original_name, originalFilename.fileName)
                else ""
            alert(getString(R.string.rename_dialog_message, name, appendix)) {
                customView {
                    input = editText(name.toString())
                }
                positiveButton(getString(R.string.ddu_rename)) {
                    input?.text?.toString()?.trim()?.let { newName: String ->
                        doRename(file, newName)
                    }
                }
                cancelButton { }
            }.show()
        }
    }

    private fun doRename(file: File, newName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadingDdus {
                val newFilename = Filename("$newName.ddu")
                val newFile = File(file.parentFile.absolutePath, newFilename.toString())
                val success = file.renameTo(newFile)
                Log.i(TAG, "$file -> $newFile: $success")
                if (success) {
                    dduFileRepository.updateFilename(file.filename, newFilename = newFilename)
                    withContext(Dispatchers.Main) {
                        toast(getString(R.string.ddu_rename_toast, file.fileName, newName))
                        dduFileDeltaList.update(file, newFile)
                    }
                } else {
                    Log.w(TAG, "failed to rename $file to $newFile")
                }
            }
        }
    }

    private fun deleteDduFile(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadingDdus {
                dduFileRepository.deleteIfExists(file.filename)
                file.delete()
            }
            withContext(Dispatchers.Main) {
                dduFileDeltaList.remove(file)
                viewModel.forgetPreviewOf(file)
                toast(getString(R.string.ddu_delete_toast, file.fileName))
            }
        }
    }

    private fun restoreDduFile(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadingDdus {
                val restoredOriginal: Filename? =
                    extractDduFrom(
                        file.filename, dduDir, dduFileRepository,
                        TAG
                    )
                restoredOriginal?.let {
                    withContext(Dispatchers.Main) {
                        toast(getString(R.string.ddu_restore_toast, file.fileName, restoredOriginal.fileName))
                        dduFileDeltaList.addBefore(file, dir / restoredOriginal)
                    }
                }
            }
        }
    }

    private fun duplicateDduFile(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadingDdus {
                val newFile = withUniquePostfix(file)
                copyFile(file, newFile)
                dduFileRepository.duplicate(file.filename, newFile.filename)
                withContext(Dispatchers.Main) {
                    toast(getString(R.string.ddu_duplicate_toast, file.fileName, newFile.fileName))
                    dduFileDeltaList.addAfter(file, newFile)
                }
            }
        }
    }

    private fun requestExportDduFile(file: File) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        requestedDduFile = file
        startActivityForResult(intent, EXPORT_DDU_REQUEST_CODE)
    }

    private fun exportDduFile(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadingDdus {
                requestedDduFile?.let { file ->
                    contentResolver.openOutputStream(uri)?.let { outputStream ->
                        Log.i(TAG, "exporting file \"${file.name}\"")
                        copyStream(file.inputStream(), outputStream)
                    }
                    requestedDduFile = null
                    withContext(Dispatchers.Main) {
                        toast(getString(R.string.ddu_exported_toast, file.fileName))
                    }
                }
            }
        }
    }

    private fun requestExportDduFileForDodecaLook(file: File) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        requestedDduFile = file
        startActivityForResult(intent, EXPORT_DDU_FOR_DODECA_LOOK_REQUEST_CODE)
    }

    private fun exportDduFileForDodecaLook(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadingDdus {
                requestedDduFile?.let { file ->
                    contentResolver.openOutputStream(uri)?.let { outputStream ->
                        Log.i(TAG, "exporting file \"${file.name}\" in DodecaLook-compatible format")
                        Ddu.fromFile(file).saveToStreamForDodecaLook(outputStream)
                    }
                    withContext(Dispatchers.Main) {
                        toast(getString(R.string.ddu_exported_for_dodecalook_toast, file.fileName))
                    }
                    requestedDduFile = null
                }
            }
        }
    }

    private fun deleteDir(dir: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadingDdus {
                dir.walkTopDown().iterator().forEach {
                    if (it.isDdu) dduFileRepository.deleteIfExists(it.filename)
                }
                val success = dir.deleteRecursively()
                if (!success)
                    Log.w(TAG, "failed to delete directory \"$dir\"")
                withContext(Dispatchers.Main) {
                    dirDeltaList.remove(dir)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val maybeUri: Uri? by lazy { data?.data }
        if (resultCode == Activity.RESULT_OK)
            when (requestCode) {
                IMPORT_DIR_REQUEST_CODE -> {
                    maybeUri?.let { uri ->
                        DocumentFile.fromTreeUri(this, uri)
                            ?.let { importDduDir(it) }
                    }
                }
                IMPORT_DDUS_REQUEST_CODE ->
                    (data?.clipData?.let { clipData ->
                        (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
                    } ?: maybeUri?.let { listOf(it) })
                        ?.let { uris -> importDdus(uris) }
                EXPORT_DDU_REQUEST_CODE ->
                    maybeUri?.let { uri -> exportDduFile(uri) }
                EXPORT_DDU_FOR_DODECA_LOOK_REQUEST_CODE ->
                    maybeUri?.let { uri -> exportDduFileForDodecaLook(uri) }
                EXPORT_DIR_REQUEST_CODE ->
                    maybeUri?.let { uri -> exportDduDir(uri) }
                else -> super.onActivityResult(requestCode, resultCode, data)
            }
    }

    companion object {
        private const val TAG: String = "DduChooserActivity"
        private const val IMPORT_DIR_REQUEST_CODE = 1
        private const val IMPORT_DDUS_REQUEST_CODE = 2
        private const val EXPORT_DIR_REQUEST_CODE = 3
        private const val EXPORT_DDU_REQUEST_CODE = 4
        private const val EXPORT_DDU_FOR_DODECA_LOOK_REQUEST_CODE = 5
        private val DEFAULT_DDU_FILENAME = Filename("untitled.ddu")
    }
}

interface ContextMenuManager {
    fun registerViewForContextMenu(view: View)
    fun createMenu(@MenuRes menuRes: Int, menu: Menu, contextMenuSource: ContextMenuSource)
    fun registerForContextMenu(view: View, @MenuRes menuRes: Int, contextMenuSource: ContextMenuSource) {
        registerViewForContextMenu(view)
        view.setOnCreateContextMenuListener { menu, _, _ ->
            createMenu(menuRes, menu, contextMenuSource)
        }
    }
}

sealed class ContextMenuSource {
    data class Dir(val dir: File) : ContextMenuSource()
    data class DduFile(val file: File) : ContextMenuSource()
}
