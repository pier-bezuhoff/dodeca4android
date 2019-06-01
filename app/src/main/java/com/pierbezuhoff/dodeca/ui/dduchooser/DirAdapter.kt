package com.pierbezuhoff.dodeca.ui.dduchooser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.utils.Sleeping
import kotlinx.android.synthetic.main.dir_row.view.*
import java.io.File

class DirAdapter(
    private val activity: DduChooserActivity,
    private val dduDir: File
) : RecyclerView.Adapter<DirAdapter.DirViewHolder>() {
    interface DirChangeListener { fun onDirChange(dir: File) }
    class DirViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    lateinit var model: DduChooserViewModel
    private val dir: File get() = model.
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