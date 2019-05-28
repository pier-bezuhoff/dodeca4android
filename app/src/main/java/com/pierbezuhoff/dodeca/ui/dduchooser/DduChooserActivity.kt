package com.pierbezuhoff.dodeca.ui.dduchooser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.withStyledAttributes
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.models.DduFileRepository
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManagerFactory
import com.pierbezuhoff.dodeca.ui.dodeca.MainViewModel
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.utils.FileName
import com.pierbezuhoff.dodeca.utils.Filename
import com.pierbezuhoff.dodeca.utils.Sleeping
import com.pierbezuhoff.dodeca.utils.copyDirectory
import com.pierbezuhoff.dodeca.utils.copyFile
import com.pierbezuhoff.dodeca.utils.copyStream
import com.pierbezuhoff.dodeca.utils.dduDir
import com.pierbezuhoff.dodeca.utils.extractDduFrom
import com.pierbezuhoff.dodeca.utils.fileName
import com.pierbezuhoff.dodeca.utils.filename
import com.pierbezuhoff.dodeca.utils.getDisplayName
import com.pierbezuhoff.dodeca.utils.isDdu
import com.pierbezuhoff.dodeca.utils.stripDdu
import com.pierbezuhoff.dodeca.utils.withUniquePostfix
import kotlinx.android.synthetic.main.activity_dduchooser.*
import kotlinx.android.synthetic.main.dir_row.view.*
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import org.jetbrains.anko.alert
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.customView
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.editText
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import org.jetbrains.anko.yesButton
import java.io.File

// TODO: refactor with databindings, viewmodel and coroutines|paging + DI (koin)
// MAYBE: action bar: search by name
// MAYBE: store in sharedPreferences last dir
// MAYBE: link to external folder
class DduChooserActivity : AppCompatActivity() {
    private val optionsManager by lazy {
        OptionsManager(defaultSharedPreferences)
    }
    private val dodecaFactory by lazy {
        DodecaAndroidViewModelWithOptionsManagerFactory(application, optionsManager)
    }
    private val mainViewModel by lazy {
        ViewModelProviders.of(this, dodecaFactory).get(MainViewModel::class.java)
    }
    private val model by lazy {
        ViewModelProviders.of(this).get(DduChooserViewModel::class.java)
    }
    private val dduFileRepository =
        DduFileRepository.get(this)
    private lateinit var dduAdapter: DduAdapter
    private lateinit var dirAdapter: DirAdapter
    private var requestedDduFile: File? = null
    private var requestedDduDir: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dduchooser)
        dirAdapter = DirAdapter(this, dduDir)
        dir_recycler_view.apply {
            adapter = dirAdapter
            layoutManager = LinearLayoutManager(this@DduChooserActivity)
            itemAnimator = DefaultItemAnimator()
        }
        // TODO: move to AsyncTask
//        val progressBar = ContentLoadingProgressBar(this)
//        progressBar.show()
        dduAdapter = DduAdapter(this, ::onChoose)
//        progressBar.hide()
        ddu_recycler_view.apply {
            adapter = dduAdapter
            // colors and thickness are setToIn from styles.xml
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.HORIZONTAL))
            itemAnimator = DefaultItemAnimator()
            setHasFixedSize(true)
        }
    }

    private fun initAdapter() {
        val adapter = DduFileAdapter()
        ddu_recycler_view.adapter = adapter
        model.dduFiles.observe(this) {
            // MAYBE: handle empty list case
            adapter.submitList(it)
        }
    }

    private fun onChoose(file: File) {
        Log.i(TAG, "File \"${file.absolutePath}\" chosen")
        val data = Intent().apply {
            putExtra("dirPath", dir.absolutePath)
            putExtra("path", file.absolutePath)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.ddu_chooser_appbar, menu)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            menu?.removeItem(R.id.import_dir)
            menu?.removeItem(R.id.export_ddus) // TODO: add this
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isSet = true
        when (item.itemId) {
            R.id.to_parent_dir -> if (dir.absolutePath != dduDir.absolutePath) onDirChange(dir.parentFile)
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
        dduAdapter.contextMenuCreatorPosition?.let { position ->
            val file = dduAdapter.files[position]
            when (item?.itemId) {
                R.id.ddu_rename -> renameDduFile(file, position)
                R.id.ddu_delete -> deleteDduFile(file, position)
                R.id.ddu_restore -> restoreDduFile(file)
                R.id.ddu_duplicate -> duplicateDduFile(file)
                R.id.ddu_export -> requestExportDduFile(file)
                R.id.ddu_export_for_dodecalook -> requestExportDduFileForDodecaLook(file)
                else -> isSet1 = false
            }
            dduAdapter.contextMenuCreatorPosition = null
        }
        var isSet2 = true
        dirAdapter.contextMenuCreatorPosition?.let { position ->
            val dir = dirAdapter.dirs[position]
            when (item?.itemId) {
                R.id.dir_delete -> deleteDir(dir)
                R.id.dir_export -> requestExportDduDir(dir)
                else -> isSet2 = false
            }
        }
        return isSet1 || isSet2 || super.onContextItemSelected(item)
    }

    private fun renameDduFile(file: File, position: Int) {
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
                    lifecycleScope.launch {
                        input?.text?.toString()?.trim()?.let { newName: FileName ->
                            val newFilename = "$newName.ddu"
                            val newFile = File(file.parentFile.absolutePath, newFilename)
                            val success = file.renameTo(newFile)
                            Log.i(TAG, "$file -> $newFile: $success")
                            if (success) {
                                toast(getString(R.string.ddu_rename_toast, name, newName))
                                dduFileRepository.updateFilenameInserting(file.name, newFilename = newFilename)
                                val preview: Bitmap? = dduFileRepository.getPreview(newFilename)
                                with(dduAdapter) {
                                    files[position] = newFile
                                    previews[newFilename] = preview
                                    refreshDir()
                                }
                            } else {
                                Log.w(TAG, "failed to rename $file to $newFile")
                            }
                        }
                    }
                }
                cancelButton { }
            }.show()
        }
    }

    private fun deleteDduFile(file: File, position: Int) {
        toast(getString(R.string.ddu_delete_toast, file.nameWithoutExtension))
        lifecycleScope.launch {
            dduFileRepository.delete(file.name)
        }
        file.delete()
        dduAdapter.files.removeAt(position)
        dduAdapter.notifyDataSetChanged()
    }

    private fun restoreDduFile(file: File) {
        // TODO: restore imported files by original path
        lifecycleScope.launch {
            val original: Filename? = extractDduFrom(
                file.name, dduDir, dduFileRepository,
                TAG
            )
            original?.let {
                toast(getString(R.string.ddu_restore_toast, file.nameWithoutExtension, original.stripDdu()))
                with(dduAdapter) {
                    previews[original] = null
                    buildings.remove(original)
                    refreshDir()
                }
            }
        }
    }

    private fun duplicateDduFile(file: File) {
        val newFile = withUniquePostfix(file)
        copyFile(file, newFile)
        toast(getString(R.string.ddu_duplicate_toast, file.nameWithoutExtension, newFile.nameWithoutExtension))
        dduAdapter.refreshDir()
    }

    private fun deleteDir(dir: File) {
        lifecycleScope.launch {
            dir.walkTopDown().iterator().forEach {
                if (it.isDdu) dduFileRepository.delete(it.name)
            }
            val success = dir.deleteRecursively()
            if (!success)
                Log.w(TAG, "failed to delete directory \"$dir\"")
            dirAdapter.refreshDir()
        }
    }

    private fun toggleFolders() {
        dir_recycler_view.visibility = when(dir_recycler_view.visibility) {
            View.GONE -> View.VISIBLE
            View.VISIBLE -> View.GONE
            else -> dir_recycler_view.visibility
        }
    }

    private fun requestImportDduDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
//                putExtra("android.content.extra.SHOW_ADVANCED", true)
//                putExtra("android.content.extra.FANCY", true)
            }
            startActivityForResult(intent,
                IMPORT_DIR_REQUEST_CODE
            )
        } // NOTE: Android < 5 has no Intent.ACTION_OPEN_DOCUMENT_TREE
    }

    private fun requestImportDdus() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent,
            IMPORT_DDUS_REQUEST_CODE
        )
//        startActivityForResult(Intent.createChooser(intent, "Select ddu-files"), IMPORT_DDUS_REQUEST_CODE)
    }

    private fun importDduDir(source: DocumentFile) { // unused now
        Log.i(TAG, "importing dir \"${source.name}\"")
        toast(getString(R.string.dir_importing_toast, source.name))
        val target = File(dduAdapter.dir, source.name)
        copyDirectory(contentResolver, source, target)
        onDirChange(dir)
    }

    private fun importDdus(uris: List<Uri>) {
        uris.forEach { uri ->
            DocumentFile.fromSingleUri(this, uri)?.let { file ->
                val filename: Filename = file.name
                    ?: contentResolver.getDisplayName(uri)?.let { if ('.' !in it) "$it.ddu" else it }
                    ?: "untitled.ddu" // MAYBE: try smth else to extract filename!
                val target0 = File(dir, filename)
                val target: File =
                    if (target0.exists()) withUniquePostfix(target0)
                    else target0
                Log.i(TAG, "importing file \"${target.name}\" from \"${file.uri}\"")
                copyFile(contentResolver, file, target)
            }
        }
        dduAdapter.refreshDir()
    }

    private fun requestExportDduDir(targetDir: File = dir) {
        requestedDduDir = targetDir
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                // Documents.FLAG_DIR_SUPPORTS_CREATE
            }
            startActivityForResult(intent,
                EXPORT_DIR_REQUEST_CODE
            )
        } else {
            toast("TODO: import directory $targetDir (for now works on Android 5.0+)")
            // TODO: find other way around
/*                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                putExtra(Intent.EXTRA_TITLE, targetDir.name)
                addCategory(Intent.CATEGORY_ALTERNATIVE)
            }*/
        }
    }

    private fun exportDduDir(uri: Uri) {
        requestedDduDir?.let { dir ->
            Log.i(TAG, "exporting dir \"${dir.name}\"")
            toast(getString(R.string.dir_exporting_toast, dir.name))
            // maybe: use File.walkTopDown()
            // TODO: show progress bar or smth
            DocumentFile.fromTreeUri(this, uri)?.let { targetDir ->
                exportDduDocumentFile(dir, targetDir)
            }
            requestedDduDir = null
        }
    }

    private fun deleteAll() {
        alert(
            R.string.delete_all_alert_message,
            R.string.delete_all_alert_title
        ) {
            yesButton {
                lifecycleScope.launch {
                    dir.walkTopDown().iterator().forEach {
                        if (it.isDdu) dduFileRepository.delete(it.name)
                    }
                    FileUtils.cleanDirectory(dir)
                    onDirChange(dir)
                }
            }
            cancelButton { }
        }.show()
    }

    private fun exportDduDocumentFile(source: File, targetDir: DocumentFile) {
        if (source.isDirectory) {
            targetDir.createDirectory(source.name)?.let { newDir ->
                source.listFiles().forEach { exportDduDocumentFile(it, newDir) }
            }
        } else if (source.isDdu) {
            targetDir.createFile("*/*", source.name)?.let { newFile ->
                contentResolver.openOutputStream(newFile.uri)?.let { outputStream ->
                    copyStream(source.inputStream(), outputStream)
                }
            }
        }
    }

    private fun requestExportDduFile(file: File) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // maybe: "text/plain"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        requestedDduFile = file
        startActivityForResult(intent,
            EXPORT_DDU_REQUEST_CODE
        )
    }

    private fun exportDduFile(uri: Uri) {
        requestedDduFile?.let { file ->
            contentResolver.openOutputStream(uri)?.let { outputStream ->
                Log.i(TAG, "exporting file \"${file.name}\"")
                copyStream(file.inputStream(), outputStream)
            }
            requestedDduFile = null
        }
    }

    private fun requestExportDduFileForDodecaLook(file: File) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // maybe: "text/plain"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        requestedDduFile = file
        startActivityForResult(intent,
            EXPORT_DDU_FOR_DODECALOOK_REQUEST_CODE
        )
    }

    private fun exportDduFileForDodecaLook(uri: Uri) {
        lifecycleScope.launch {
            requestedDduFile?.let { file ->
                contentResolver.openOutputStream(uri)?.let { outputStream ->
                    Log.i(TAG, "exporting file \"${file.name}\" in DodecaLook-compatible format")
                    Ddu.fromFile(file).saveToStreamForDodecaLook(outputStream)
                }
                requestedDduFile = null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK)
            when (requestCode) {
                IMPORT_DIR_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        DocumentFile.fromTreeUri(this, uri)?.let { importDduDir(it) }
                    }
                }
                IMPORT_DDUS_REQUEST_CODE ->
                    (data?.clipData?.let { clipData ->
                        (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
                    } ?: data?.data?.let { listOf(it) })?.let { uris ->
                        importDdus(uris)
                    }
                EXPORT_DDU_REQUEST_CODE -> data?.data?.let { uri -> exportDduFile(uri) }
                EXPORT_DDU_FOR_DODECALOOK_REQUEST_CODE -> data?.data?.let { uri -> exportDduFileForDodecaLook(uri) }
                EXPORT_DIR_REQUEST_CODE -> data?.data?.let { uri -> exportDduDir(uri) }
                else -> super.onActivityResult(requestCode, resultCode, data)
            }
    }

    fun onDirChange(newDir: File) {
        dir = newDir
        dduAdapter.refreshDir()
        dirAdapter.refreshDir()
    }

    companion object {
        private const val TAG: String = "DduChooserActivity"
        private const val IMPORT_DIR_REQUEST_CODE = 1
        private const val IMPORT_DDUS_REQUEST_CODE = 2
        private const val EXPORT_DIR_REQUEST_CODE = 3
        private const val EXPORT_DDU_REQUEST_CODE = 5
        private const val EXPORT_DDU_FOR_DODECALOOK_REQUEST_CODE = 6
    }
}

class AutofitGridRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    private val manager = GridLayoutManager(context,
        defaultNColumns
    )
    private var columnWidth: Int? = null

    init {
        context.withStyledAttributes(attrs, intArrayOf(android.R.attr.columnWidth), defStyleAttr) {
            getDimensionPixelSize(0, -1).let {
                columnWidth = if (it == -1) null else it
            }
        }
        layoutManager = manager
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        val spanCount = Math.max(minNColumns, measuredWidth / (columnWidth ?: defaultColumnWidth))
        manager.spanCount = spanCount
    }

    companion object {
        const val defaultNColumns = 2
        const val minNColumns = 1 // I'd like at least 2, check on small phones
        const val cellPadding = 8
        val defaultColumnWidth: Int get() = 2 * cellPadding + values.previewSizePx
    }
}

class DirAdapter(
    private val activity: DduChooserActivity,
    private val dduDir: File
) : RecyclerView.Adapter<DirAdapter.DirViewHolder>() {
    class DirViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    private val dir: File get() = activity.dir
    private val sleepingDirs: Sleeping<List<File>> =
        Sleeping { dir.listFiles { file -> file.isDirectory }.toList() }
    val dirs: List<File> by sleepingDirs
    var contextMenuCreatorPosition: Int? = null // track VH to act on it

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirViewHolder =
        DirViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.dir_row, parent, false)
        )

    override fun onBindViewHolder(holder: DirViewHolder, position: Int) {
        val dir = dirs[position]
        with(holder.view) {
            dir_name.text = dir.name
            setOnClickListener { downDir(dir) }
            activity.registerForContextMenu(this)
            setOnCreateContextMenuListener { menu, _, _ ->
                contextMenuCreatorPosition = position
                activity.menuInflater.inflate(R.menu.ddu_chooser_dir_context_menu, menu)
            }
        }
    }

    fun refreshDir() {
        sleepingDirs.awake()
        notifyDataSetChanged()
    }

    private fun downDir(newDir: File) {
        activity.onDirChange(newDir)
    }

    override fun getItemCount(): Int = dirs.size
}

class DduAdapter(
    private val activity: DduChooserActivity,
    private val onChoose: (File) -> Unit
) : RecyclerView.Adapter<DduAdapter.DduViewHolder>() {
    class DduViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    val dir: File get() = activity.dir
    var contextMenuCreatorPosition: Int? = null // track VH to act on it
    private val sleepingFiles: Sleeping<ArrayList<File>> =
        Sleeping {
            ArrayList(dir.listFiles().filter { it.extension.toLowerCase() == "ddu" })
        }
    val files: ArrayList<File> by sleepingFiles
    private val dduFileRepository = DduFileRepository.get(activity)
    val previews: MutableMap<Filename, Bitmap?> = mutableMapOf()
    val buildings: MutableMap<Filename, DduViewHolder?> = mutableMapOf()

    init {
        // maybe: in async task; show ContentLoadingProgressBar or ProgressDialog
        // ISSUE: may lead to OutOfMemoryError (fast return to after opening a ddu)
        activity.lifecycleScope.launch {
            dduFileRepository.getAllDduFilenamesAndPreviews().forEach { (filename, preview) ->
                previews[filename] = preview
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DduViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.ddu_item, parent, false)
        return DduViewHolder(textView)
    }

    override fun onBindViewHolder(holder: DduViewHolder, position: Int) {
        val file = files[position]
        val filename: Filename = file.name
        val bitmap: Bitmap? = previews[filename]
        with(holder.view) {
            val fileName = file.nameWithoutExtension
            findViewById<TextView>(R.id.ddu_entry).text = fileName
            setOnClickListener { onItemClick(file) }
            activity.registerForContextMenu(this)
            setOnCreateContextMenuListener { menu, _, _ ->
                contextMenuCreatorPosition = position
                activity.menuInflater.inflate(R.menu.ddu_chooser_context_menu, menu)
            }
        }
        holder.setIsRecyclable(false)
        val preview: ImageView = holder.view.findViewById(R.id.ddu_preview)
        val progressBar: ProgressBar = holder.view.findViewById(R.id.preview_progress)
        if (bitmap != null) {
            preview.setImageBitmap(bitmap)
            progressBar.visibility = View.GONE
        } else {
            progressBar.visibility = View.VISIBLE
            preview.visibility = View.GONE
            buildPreviewAsync(file, holder)
        }
    }

    private fun buildPreviewAsync(file: File, holder: DduViewHolder) {
        // maybe: use some sync primitives
        val fileName = file.name
        if (fileName !in buildings.keys) {
            buildings[fileName] = holder
            doAsync {
                activity.lifecycleScope.launch {
                    val ddu = Ddu.fromFile(file)
                    // val size: Int = (0.4 * width).roundToInt() // width == height
                    val size = values.previewSizePx
                    val bitmap = ddu.buildPreview(size, size)
                    previews[fileName] = bitmap
                    dduFileRepository.setPreviewInserting(file.name, newPreview = bitmap)
                    buildings[fileName]?.let { currentHolder ->
                        uiThread {
                            val preview: ImageView = currentHolder.view.findViewById(R.id.ddu_preview)
                            val progressBar: ProgressBar = currentHolder.view.findViewById(R.id.preview_progress)
                            preview.setImageBitmap(bitmap)
                            preview.visibility = View.VISIBLE
                            progressBar.visibility = View.GONE
                        }
                    }
                }
                Unit
            }
        } else {
            buildings[fileName] = holder
        }
    }

    fun refreshDir() {
        sleepingFiles.awake()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = files.size

    private fun onItemClick(item: File) {
        if (item.isDirectory) {
            // impossible now
//            dir = item
//            refreshDir()
        } else {
            onChoose(item)
        }
    }
}