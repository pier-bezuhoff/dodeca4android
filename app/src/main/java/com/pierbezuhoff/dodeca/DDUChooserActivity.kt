package com.pierbezuhoff.dodeca

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File

typealias Filename = String

// TODO: store in sharedPreferences last dir
// TODO: preview for ddu
// TODO: go to parent folder
// MAYBE: link to external folder
class DDUChooserActivity : AppCompatActivity() {
    private lateinit var dduDir: File
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: RecyclerView.Adapter<*>
    private lateinit var viewManager: RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dduDir = File(filesDir, "ddu")
        intent.getStringExtra("dirPath")?.let {
            dduDir = File(it)
        }
        setContentView(R.layout.activity_dduchooser)
        viewAdapter = DDUAdapter(dduDir, ::onChoose)
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
        // maybe: customize for different screens in res/values/dimens.xml:
        // <resources>
        //    <dimen name="column_width">320dp</dimen>
        // </resources>
        // or better customize preview size
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
        const val defaultColumnWidth = 16 + DDUAdapter.PREVIEW_SIZE
    }
}

class DDUAdapter(
    private var dir: File,
    private val onChoose: (File) -> Unit
) : RecyclerView.Adapter<DDUAdapter.DDUViewHolder>() {
    class DDUViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    private var files: Array<File> = dir.listFiles()
        .filter { it.extension.toLowerCase() == "ddu" }
        .toTypedArray()
    private val dduFileDao: DDUFileDao by lazy { DB.dduFileDao() }
    private val previews: MutableMap<Filename, Bitmap?> = mutableMapOf()
    private val building: MutableSet<Filename> = mutableSetOf()

    init {
        // maybe: in async task; show ContentLoadingProgressBar
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
        val fileName: Filename = file.name
        val bitmap: Bitmap? = previews[fileName]
        with(holder.view) {
            val filename = file.name.removeSuffix(".ddu").removeSuffix(".DDU")
            findViewById<TextView>(R.id.ddu_entry).text = filename
            setOnClickListener { onItemClick(file) }
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
            buildPreviewAsync(file, preview, progressBar)
        }
    }

    private fun buildPreviewAsync(file: File, preview: ImageView, progressBar: ProgressBar) {
        // ?BUG?: eternal loading
        // maybe: use some sync primitives
        if (file.name !in building) {
            building.add(file.name)
            doAsync {
                val ddu = DDU.readFile(file)
                // val size: Int = (0.4 * width).roundToInt() // width == height
                val bitmap = ddu.preview(PREVIEW_SIZE, PREVIEW_SIZE)
                previews[file.name] = bitmap
                dduFileDao.insertOrUpdate(file.name) { it.preview = bitmap; it }
                uiThread {
                    preview.setImageBitmap(bitmap)
                    preview.visibility = View.VISIBLE
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    override fun getItemCount(): Int = files.size

    private fun onItemClick(item: File) {
        if (item.isDirectory) {
            dir = item
            files = dir.listFiles()
            notifyDataSetChanged()
        } else {
            onChoose(item)
        }
    }

    companion object {
        const val PREVIEW_SIZE = 300 // maybe: customize in res/values/dimens.xml
        const val TAG = "DDUAdapter"
    }
}

