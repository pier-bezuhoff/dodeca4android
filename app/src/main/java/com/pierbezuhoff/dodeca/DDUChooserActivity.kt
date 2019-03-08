package com.pierbezuhoff.dodeca

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
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_dduchooser.*
import kotlinx.android.synthetic.main.dir_row.view.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.customView
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.editText
import org.jetbrains.anko.longToast
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.io.File

// TODO: action bar: import file/folder, export folder
// MAYBE: action bar: search by name
// MAYBE: store in sharedPreferences last dir
// MAYBE: link to external folder
class DDUChooserActivity : AppCompatActivity() {
    lateinit var dir: File // current
    private lateinit var dduAdapter: DDUAdapter
    private lateinit var dirAdapter: DirAdapter
    private var requestedDDUFile: File? = null
    private var requestedDDUDir: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dir = dduDir
        intent.getStringExtra("dirPath")?.let { dir = File(it) }
        setContentView(R.layout.activity_dduchooser)
        dirAdapter = DirAdapter(this, dduDir)
        dir_recycler_view.apply {
            adapter = dirAdapter
            layoutManager = LinearLayoutManager(this@DDUChooserActivity)
            itemAnimator = DefaultItemAnimator()
        }
        dduAdapter = DDUAdapter(this, ::onChoose)
        ddu_recycler_view.apply {
            adapter = dduAdapter
            // colors and thickness are set from styles.xml
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.HORIZONTAL))
            itemAnimator = DefaultItemAnimator()
            setHasFixedSize(true)
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
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isSet = true
        when (item.itemId) {
            R.id.to_parent_folder -> if (dir.absolutePath != dduDir.absolutePath) onDirChange(dir.parentFile)
            R.id.import_ddus -> requestImportDDUDir()
            R.id.export_ddus -> requestExportDDUDir()
            else -> isSet = false
        }
        return isSet || super.onOptionsItemSelected(item)
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        var isSet = true
        dduAdapter.contextMenuCreatorPosition?.let { position ->
            val file = dduAdapter.files[position]
            when (item?.itemId) {
                R.id.ddu_rename -> renameDDUFile(file, position)
                R.id.ddu_delete -> deleteDDUFile(file, position)
                R.id.ddu_restore -> restoreDDUFile(file)
                R.id.ddu_duplicate -> duplicateDDUFile(file)
                R.id.ddu_export -> requestExportDDUFile(file)
                R.id.ddu_export_for_dodecalook -> toast("export ${file.name} for DodecaLook")
                else -> isSet = false
            }
        }
        return isSet || super.onContextItemSelected(item)
    }

    private fun renameDDUFile(file: File, position: Int) {
        val name: FileName = file.nameWithoutExtension
        var input: EditText? = null
        val originalFilename: Filename? = DB.dduFileDao().findByFilename(file.name)?.originalFilename
        val appendix =
            if (originalFilename != null && originalFilename != file.name)
                " " + getString(R.string.rename_dialog_original_name, originalFilename.stripDDU())
            else ""
        alert(getString(R.string.rename_dialog_message, name, appendix)) {
            customView {
                input = editText(name)
            }
            positiveButton(getString(R.string.ddu_rename)) {
                input?.text?.toString()?.trim()?.let { newName: FileName ->
                    val newFilename = "$newName.ddu"
                    val newFile = File(file.parentFile.absolutePath, newFilename)
                    val success = file.renameTo(newFile)
                    Log.i(TAG, "$file -> $newFile: $success")
                    if (success) {
                        toast(getString(R.string.ddu_rename_toast, name, newName))
                        val preview: Bitmap? = with(DB.dduFileDao()) {
                            insertOrUpdate(file.name) { this.filename = newFilename }
                            findByFilename(newFilename)?.preview
                        }
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
            cancelButton { }
        }.show()
    }

    private fun deleteDDUFile(file: File, position: Int) {
        toast(getString(R.string.ddu_delete_toast, file.nameWithoutExtension))
        with(DB.dduFileDao()) {
            findByFilename(file.name)?.let { delete(it) }
        }
        file.delete()
        dduAdapter.files.removeAt(position)
        dduAdapter.notifyDataSetChanged()
    }

    private fun restoreDDUFile(file: File) {
        // TODO: restore imported files by original path
        val original: Filename? = extract1DDU(file.name, dduDir, DB.dduFileDao(), TAG)
        original?.let {
            toast(getString(R.string.ddu_restore_toast, file.nameWithoutExtension, original.stripDDU()))
            with(dduAdapter) {
                previews[original] = null
                buildings.remove(original)
                refreshDir()
            }
        }
    }

    private fun duplicateDDUFile(file: File) {
        val fileName = file.nameWithoutExtension
        val part1 = Regex("^(.*)-(\\d*)$")
        fun namePart1(s: String): String = part1.find(s)?.groupValues?.let { it[1] } ?: s
        val name = namePart1(fileName)
        val postfixes: Set<Int> = dduAdapter.files
            .map {
                it.nameWithoutExtension.let { name ->
                    part1.find(name)?.groupValues
                        ?.let { it[1] to it[2].toInt() }
                        ?: name to null
                }
            }
            .filter { (_name, postfix) -> _name == name && postfix != null }
            .map { it.second!! }
            .toSet()
        val newPostfix = generateSequence(1, Int::inc)
            .filter { it !in postfixes }
            .first()
        val newFileName = "$name-$newPostfix"
        val newFile = File(dduAdapter.dir, "$newFileName.ddu")
        copyFile(file, newFile)
        toast("Duplicate of \"$fileName\" saved as \"$newFileName\"")
        dduAdapter.refreshDir()
    }

    private fun requestImportDDUDir() {
        // maybe: choose file, than import its parent
        // maybe: Intent.ACTION_OPEN_DOCUMENT_TREE or Intent.ACTION_GET_CONTENT
        // note: EXTRA_ALLOW_MULTIPLE
        val intent: Intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
//                putExtra("android.content.extra.SHOW_ADVANCED", true)
//                putExtra("android.content.extra.FANCY", true)
            }
        } else { // ISSUE: parentFile always == null
            TODO("VERSION.SDK_INT < LOLLIPOP")
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            longToast("Please select a file inside of desired folder")
        }
        startActivityForResult(intent, IMPORT_DIR_REQUEST_CODE)
    }

    private fun importDDUDir(source: DocumentFile) {
        Log.i(TAG, "importing dir \"${source.name}\"")
        toast("importing dir \"${source.name}\"")
        val target = File(dduAdapter.dir, source.name)
        copyDirectory(contentResolver, source, target)
        onDirChange(dir)
    }

    private fun requestExportDDUDir() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                // Documents.FLAG_DIR_SUPPORTS_CREATE
            }
        } else {
            TODO("VERSION.SDK_INT < LOLLIPOP")
        }
        requestedDDUDir = dir
        startActivityForResult(intent, EXPORT_DIR_REQUEST_CODE)
    }

    private fun exportDDUDir(uri: Uri) {
        requestedDDUDir?.let { dir ->
            Log.i(TAG, "exporting dir \"${dir.name}\"")
            toast("exporting dir \"${dir.name}\"")
            // maybe: use File.walkTopDown()
            DocumentFile.fromTreeUri(this, uri)?.let { targetDir ->
                exportDDUDocumentFile(dir, targetDir)
            }
            requestedDDUDir = null
        }
    }

    private fun exportDDUDocumentFile(source: File, targetDir: DocumentFile) {
        if (source.isDirectory) {
            targetDir.createDirectory(source.name)?.let { newDir ->
                source.listFiles().forEach { exportDDUDocumentFile(it, newDir) }
            }
        } else if (source.isDDU) {
            targetDir.createFile("*/*", source.name)?.let { newFile ->
                contentResolver.openOutputStream(newFile.uri)?.let { outputStream ->
                    copyStream(source.inputStream(), outputStream)
                }
            }
        }
    }

    private fun requestExportDDUFile(file: File) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // maybe: "text/plain"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        requestedDDUFile = file
        startActivityForResult(intent, EXPORT_DDU_REQUEST_CODE)
    }

    private fun exportDDUFile(uri: Uri) {
        requestedDDUFile?.let { file ->
            contentResolver.openOutputStream(uri)?.let { outputStream ->
                Log.i(TAG, "exporting file \"${file.name}\"")
                copyStream(file.inputStream(), outputStream)
            }
            requestedDDUFile = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK)
            when (requestCode) {
                IMPORT_DIR_REQUEST_CODE -> data?.data?.let { uri ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        DocumentFile.fromTreeUri(this, uri)?.let { importDDUDir(it) }
                    } else { // ISSUE: parentFile always == null
                        DocumentFile.fromSingleUri(this, uri)?.parentFile?.let { importDDUDir(it) }
                    }
                }
                EXPORT_DDU_REQUEST_CODE -> data?.data?.let { uri -> exportDDUFile(uri) }
                EXPORT_DIR_REQUEST_CODE -> data?.data?.let { uri ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        exportDDUDir(uri)
                    } else { // ISSUE: parentFile always == null
                        DocumentFile.fromSingleUri(this, uri)?.parentFile?.uri?.let { exportDDUDir(it) }
                    }
                }
                else -> super.onActivityResult(requestCode, resultCode, data)
            }
    }

    fun onDirChange(newDir: File) {
        dir = newDir
        dduAdapter.refreshDir()
        dirAdapter.refreshDir()
    }

    companion object {
        private const val TAG: String = "DDUChooserActivity"
        private const val IMPORT_DIR_REQUEST_CODE = 1
        private const val EXPORT_DIR_REQUEST_CODE = 2
        private const val EXPORT_DDU_REQUEST_CODE = 3
    }
}

class AutofitGridRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    private val manager = GridLayoutManager(context, defaultNColumns)
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
    private val activity: DDUChooserActivity,
    private val dduDir: File
) : RecyclerView.Adapter<DirAdapter.DirViewHolder>() {
    class DirViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    val dir: File get() = activity.dir
    private val sleepingDirs: Sleeping<List<File>> = Sleeping { dir.listFiles { file -> file.isDirectory }.toList() }
    private val dirs: List<File> by sleepingDirs

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirViewHolder =
        DirViewHolder(LayoutInflater.from(parent.context)
            .inflate(R.layout.dir_row, parent, false))

    override fun onBindViewHolder(holder: DirViewHolder, position: Int) {
        val dir = dirs[position]
        with(holder.view) {
            dir_name.text = dir.name
            setOnClickListener { downDir(dir) }
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

// TODO: show folders on the top
class DDUAdapter(
    private val activity: DDUChooserActivity,
    private val onChoose: (File) -> Unit
) : RecyclerView.Adapter<DDUAdapter.DDUViewHolder>() {
    class DDUViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    val dir: File get() = activity.dir
    var contextMenuCreatorPosition: Int? = null // track VH to act on it
    private val sleepingFiles: Sleeping<ArrayList<File>> = Sleeping {
        ArrayList(dir.listFiles().filter { it.extension.toLowerCase() == "ddu" })
    }
    val files: ArrayList<File> by sleepingFiles
    private val dduFileDao: DDUFileDao by lazy { DB.dduFileDao() }
    val previews: MutableMap<Filename, Bitmap?> = mutableMapOf()
    val buildings: MutableMap<Filename, DDUViewHolder?> = mutableMapOf()

    init {
        // maybe: in async task; show ContentLoadingProgressBar or ProgressDialog
        dduFileDao.getAll().forEach {
            previews[it.filename] = it.preview
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DDUViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.ddu_item, parent, false)
        return DDUViewHolder(textView)
    }

    override fun onBindViewHolder(holder: DDUViewHolder, position: Int) {
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

    private fun buildPreviewAsync(file: File, holder: DDUViewHolder) {
        // maybe: use some sync primitives
        val fileName = file.name
        if (fileName !in buildings.keys) {
            buildings[fileName] = holder
            doAsync {
                val ddu = DDU.readFile(file)
                // val size: Int = (0.4 * width).roundToInt() // width == height
                val size = values.previewSizePx
                val bitmap = ddu.preview(size, size)
                previews[fileName] = bitmap
                dduFileDao.insertOrUpdate(file.name) { this.preview = bitmap }
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
        } else {
            buildings[fileName] = holder
        }
    }

    fun refreshDir() {
        sleepingFiles.awake()
        notifyDataSetChanged()
    }

    fun toParentDir() {
//        if (dir.absolutePath != dduDir.absolutePath) {
//            dir = dir.parentFile
//            refreshDir()
//        }
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