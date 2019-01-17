package com.pierbezuhoff.dodeca

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_dduchooser.*
import java.io.File

// TODO: store in sharedPreferences last dir
// TODO: preview for ddu
// TODO: go to parent folder
// MAYBE: link to external folder
class DDUChooserActivity : AppCompatActivity() {
    private lateinit var dduDir: File
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var viewAdapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>
    private lateinit var viewManager: androidx.recyclerview.widget.RecyclerView.LayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dduDir = File(filesDir, "ddu")
        intent.getStringExtra("dirPath")?.let {
            dduDir = File(it)
        }
        setContentView(R.layout.activity_dduchooser)

        viewManager  = androidx.recyclerview.widget.LinearLayoutManager(this) // type mismatch?
        viewAdapter = DDUAdapter(dduDir, ::onChoose)
        recyclerView = ddu_recycler_view.apply {
            layoutManager = viewManager
            adapter = viewAdapter
            addItemDecoration(
                androidx.recyclerview.widget.DividerItemDecoration(
                    context,
                    androidx.recyclerview.widget.LinearLayoutManager.VERTICAL
                )
            )
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
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

class DDUAdapter(private var dir: File, private val onChoose: (File) -> Unit) : RecyclerView.Adapter<DDUAdapter.DDUViewHolder>() {
    class DDUViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    private var files: Array<File> = dir.listFiles()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DDUViewHolder {
        val textView = LayoutInflater.from(parent.context)
            .inflate(R.layout.ddu_item, parent, false)
        return DDUViewHolder(textView)
    }

    override fun onBindViewHolder(holder: DDUViewHolder, position: Int) {
        val file = files[position]
        holder.view.findViewById<TextView>(R.id.ddu_entry).text = file.name
        holder.view.setOnClickListener { onItemClick(file) }
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
}
