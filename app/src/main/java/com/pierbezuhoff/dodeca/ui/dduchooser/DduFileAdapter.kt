package com.pierbezuhoff.dodeca.ui.dduchooser

import android.app.Activity
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.observe
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.db.DduFile
import kotlinx.android.synthetic.main.ddu_item.view.*
import java.io.File

class DduFileAdapter : PagedListAdapter<DduFile, DduFileAdapter.DduFileViewHolder>(
    DIFF_CALLBACK
) {
    private var contextMenuCreatorPosition: Int? = null
    private lateinit var model: DduChooserViewModel
    class DduFileViewHolder(val view: View) : RecyclerView.ViewHolder(view)
    data class DduFileEntry(val file: File, val bitmap: Bitmap?)

    init {
        "custom DataSource.Factory: from files in dir"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DduFileViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.ddu_item, parent, false)
        return DduFileViewHolder(view)
    }

    override fun onBindViewHolder(holder: DduFileViewHolder, position: Int) {
        val dduFile: DduFile? = getItem(position)
        dduFile?.let {
            with(holder) {
                view.ddu_entry.text = dduFile.filename.fileName.toString()
                view.setOnClickListener {
                    "capture dduFile in closure"
                }
                val activity: Activity = "pass somehow"
                activity.registerForContextMenu(view)
                view.setOnCreateContextMenuListener { menu, _, _ ->
                    contextMenuCreatorPosition = position
                    activity.menuInflater.inflate(R.menu.ddu_chooser_context_menu, menu)
                }
                setIsRecyclable(false)
                val bitmap: Bitmap? = dduFile.preview
                if (bitmap == null) {
                    noPreview(holder)
                    val file: File = "file in current dir for dduFile"
                    model.buildPreviewOf(file)
                        .observe("custom or activity" as LifecycleOwner) { newBitmap: Bitmap ->
                            setPreview(holder, newBitmap)
                        }
                } else {
                    setPreview(holder, bitmap)
                }
            }
        }
    }

    private fun noPreview(holder: DduFileViewHolder) {
        holder.view.ddu_preview.visibility = View.GONE
        holder.view.preview_progress.visibility = View.VISIBLE
    }

    private fun setPreview(holder: DduFileViewHolder, bitmap: Bitmap) {
        holder.view.ddu_preview.apply {
            visibility = View.VISIBLE
            setImageBitmap(bitmap)
        }
        holder.view.preview_progress.visibility = View.GONE
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DduFile>() {
            override fun areItemsTheSame(oldItem: DduFile, newItem: DduFile): Boolean =
                oldItem.uid == newItem.uid
            override fun areContentsTheSame(oldItem: DduFile, newItem: DduFile): Boolean =
                oldItem == newItem
        }
    }
}
