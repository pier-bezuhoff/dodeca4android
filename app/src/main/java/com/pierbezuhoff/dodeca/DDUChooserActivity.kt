package com.pierbezuhoff.dodeca

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_dduchooser.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File

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
        val nColumns = 2
        viewManager  = GridLayoutManager(this, nColumns)
        viewAdapter = DDUAdapter(dduDir, ::onChoose)
        recyclerView = ddu_recycler_view.apply {
            layoutManager = viewManager
            adapter = viewAdapter
            // colors and thickness are set from styles.xml
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.HORIZONTAL))
            itemAnimator = DefaultItemAnimator()
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

class DDUAdapter(
    private var dir: File,
    private val onChoose: (File) -> Unit
) : RecyclerView.Adapter<DDUAdapter.DDUViewHolder>() {
    class DDUViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    private var files: Array<File> = dir.listFiles()
        .filter { it.extension.toLowerCase() == "ddu" }
        .toTypedArray()
    private val dduFileDao: DDUFileDao by lazy { DB.dduFileDao() }
    val previews: MutableMap<String, Bitmap?> = mutableMapOf()

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
        val bitmap: Bitmap? = previews[file.name]

        var preview: ImageView? = null
        var progressBar: ProgressBar? = null
        val filename = file.name.removeSuffix(".ddu").removeSuffix(".DDU")
        with(holder.view) {
            findViewById<TextView>(R.id.ddu_entry).text = filename
            setOnClickListener { onItemClick(file) }
            preview = findViewById(R.id.ddu_preview)
            progressBar = findViewById(R.id.preview_progress)
        }
        if (bitmap != null) {
            preview?.setImageBitmap(bitmap)
            progressBar?.visibility = View.GONE
        } else {
            progressBar?.visibility = View.VISIBLE
            doAsync {
                val ddu = DDU.readFile(file)
                // val size: Int = (0.4 * width).roundToInt() // width == height
                val bitmap = ddu.preview(PREVIEW_SIZE, PREVIEW_SIZE)
                previews[file.name] = bitmap
                dduFileDao.insertOrUpdate(file.name) { it.preview = bitmap; it }
                uiThread {
                    preview?.setImageBitmap(bitmap)
                    progressBar?.visibility = View.GONE
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
        const val PREVIEW_SIZE = 300
    }
}

