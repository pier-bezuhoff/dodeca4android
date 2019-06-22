package com.pierbezuhoff.dodeca.ui.dduchooser

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.utils.Connection
import com.pierbezuhoff.dodeca.utils.LifecycleInheritance
import com.pierbezuhoff.dodeca.utils.LifecycleInheritor
import com.pierbezuhoff.dodeca.utils.fileName
import kotlinx.android.synthetic.main.ddu_item.view.*
import java.io.File

class DduFileAdapter
    : PagedListAdapter<File, DduFileAdapter.DduFileViewHolder>(DIFF_CALLBACK)
    , LifecycleInheritor by LifecycleInheritance()
{
    interface FileChooser { fun chooseFile(file: File) }
    interface PreviewSupplier { fun getPreviewOf(file: File): LiveData<Pair<File, Bitmap>> }

    private val fileChooserConnection = Connection<FileChooser>()
    val fileChooserSubscription = fileChooserConnection.subscription
    private val contextMenuConnection = Connection<ContextMenuManager>()
    val contextMenuSubscription = contextMenuConnection.subscription
    private val previewSupplierConnection = Connection<PreviewSupplier>()
    val previewSupplierSubscription = previewSupplierConnection.subscription

    data class Item(val file: File, val preview: Bitmap?)
    class DduFileViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        lateinit var file: File
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DduFileViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.ddu_item, parent, false)
        return DduFileViewHolder(view)
    }

    override fun onBindViewHolder(holder: DduFileViewHolder, position: Int) {
        val file: File? = getItem(position)
        file?.let {
            with(holder) {
                holder.file = file
                view.ddu_entry.text = file.fileName.toString()
                view.setOnClickListener {
                    fileChooserConnection.send { chooseFile(file) }
                }
                contextMenuConnection.send {
                    registerForContextMenu(
                        view,
                        menuRes = CONTEXT_MENU_RES,
                        contextMenuSource = ContextMenuSource.DduFile(file)
                    )
                }
                showProgressBar(holder)
                require(lifecycleInherited)
                previewSupplierConnection.send {
                    getPreviewOf(file)
                } ?.observe(this@DduFileAdapter, Observer { (file: File, newBitmap: Bitmap) ->
                    // NOTE: view holder may be recycled and re-bind-ed while we are waiting for the preview,
                    // NOTE: so we tag it with file
                    if (holder.file == file)
                        setPreview(holder, newBitmap)
                })
            }
        }
    }

    private fun showProgressBar(holder: DduFileViewHolder) {
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
        private const val TAG = "DduFileAdapter"
        @MenuRes private const val CONTEXT_MENU_RES = R.menu.ddu_chooser_context_menu
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem.absolutePath == newItem.absolutePath
            // TODO: cmp previews; preview -> item: Item(file, preview)
            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem.absolutePath == newItem.absolutePath
        }
    }
}
