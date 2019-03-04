package com.pierbezuhoff.dodeca

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_dduchooser.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.cancelButton
import org.jetbrains.anko.customView
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.editText
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.io.File

typealias FileName = String // without extension
typealias Filename = String // with extension
fun Filename.stripDDU(): FileName = this.removeSuffix(".ddu").removeSuffix(".DDU")

// TODO: action bar: import file/folder, export folder
// MAYBE: action bar: search by name
// MAYBE: store in sharedPreferences last dir
// MAYBE: go to parent folder
// MAYBE: link to external folder
class DDUChooserActivity : AppCompatActivity() {
    private lateinit var dduDir: File
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: DDUAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dduDir = File(filesDir, "ddu")
        intent.getStringExtra("dirPath")?.let {
            dduDir = File(it)
        }
        setContentView(R.layout.activity_dduchooser)
        viewAdapter = DDUAdapter(this, dduDir, ::onChoose)
        recyclerView = ddu_recycler_view.apply {
            adapter = viewAdapter
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
            R.id.to_parent_folder -> toast("to parent folder")
            R.id.import_ddus -> toast("import")
            R.id.export_ddus -> toast("export")
            else -> isSet = false
        }
        return isSet || super.onOptionsItemSelected(item)
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        val superResult = super.onContextItemSelected(item)
        var result = true
        viewAdapter.contextMenuCreatorPosition?.let { position ->
            val file = viewAdapter.files[position]
            when (item?.itemId) {
                R.id.ddu_rename -> renameDDUFile(file, position)
                R.id.ddu_delete -> deleteDDUFile(file, position)
                R.id.ddu_restore -> restoreDDUFile(file, position)
                else -> result = false
            }
        }
        return result || superResult
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
                        with(viewAdapter) {
                            files[position] = newFile
                            sleepingFiles.awake()
                            previews[newFilename] = preview
                            notifyDataSetChanged()
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
        viewAdapter.files.removeAt(position)
        viewAdapter.notifyDataSetChanged()
    }

    private fun restoreDDUFile(file: File, position: Int) {
        // TODO: restore imported files by original path
        val original: Filename? = extract1DDU(file.name, dduDir, DB.dduFileDao(), TAG)
        original?.let {
            toast(getString(R.string.ddu_restore_toast, file.nameWithoutExtension, original.stripDDU()))
            with(viewAdapter) {
                previews[original] = null
                buildings.remove(original)
                sleepingFiles.awake()
                notifyDataSetChanged()
            }
        }
    }

    companion object {
        private const val TAG: String = "DDUChooserActivity"
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

// TODO: show folders on the top
class DDUAdapter(
    private val activity: AppCompatActivity,
    private var dir: File,
    private val onChoose: (File) -> Unit
) : RecyclerView.Adapter<DDUAdapter.DDUViewHolder>() {
    class DDUViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    var contextMenuCreatorPosition: Int? = null // track VH to act on it
    val sleepingFiles: Sleeping<ArrayList<File>> = Sleeping {
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

    override fun getItemCount(): Int = files.size

    private fun onItemClick(item: File) {
        if (item.isDirectory) {
            // impossible now
            dir = item
            sleepingFiles.awake()
            notifyDataSetChanged()
        } else {
            onChoose(item)
        }
    }
}
