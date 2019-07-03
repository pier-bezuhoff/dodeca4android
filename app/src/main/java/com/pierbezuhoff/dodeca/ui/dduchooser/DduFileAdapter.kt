package com.pierbezuhoff.dodeca.ui.dduchooser

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.utils.Connection
import com.pierbezuhoff.dodeca.utils.LifecycleInheritance
import com.pierbezuhoff.dodeca.utils.LifecycleInheritor
import com.pierbezuhoff.dodeca.utils.fileName
import kotlinx.android.synthetic.main.ddu_item.view.*
import java.io.File

class DduFileAdapter(private val files: List<File>)
    : MetaRecyclerViewAdapter<DduFileAdapter.DduFileViewHolder>()
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

    private val blankPreview: Bitmap = Ddu.createBlankPreview()

    class DduFileViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        lateinit var file: File
    }

    fun findPositionOf(file: File): Int? {
        val ix = files.indexOf(file)
        return if (ix == -1) null else ix
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DduFileViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.ddu_item, parent, false)
        return DduFileViewHolder(view)
    }

    override fun onBindViewHolder(holder: DduFileViewHolder, position: Int) {
        val file: File = files[position]
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
            File("")
            previewSupplierConnection.send {
                getPreviewOf(file)
            } ?.observe(this@DduFileAdapter, Observer { (file: File, newBitmap: Bitmap) ->
                // NOTE: view holder may be recycled and re-bind-ed while we are waiting for the preview,
                //  so we tag it with [file]
                if (holder.file == file)
                    setPreview(holder, newBitmap)
            })
        }
    }

    private fun showProgressBar(holder: DduFileViewHolder) {
        holder.view.ddu_preview.apply {
            visibility = View.VISIBLE
            setImageBitmap(blankPreview)
        }
        holder.view.preview_progress.visibility = View.VISIBLE
    }

    private fun setPreview(holder: DduFileViewHolder, bitmap: Bitmap) {
        holder.view.ddu_preview.apply {
            visibility = View.VISIBLE
            setImageBitmap(bitmap)
        }
        holder.view.preview_progress.visibility = View.GONE
    }

    override fun getItemCount(): Int =
        files.size

    companion object {
        private const val TAG = "DduFileAdapter"
        @MenuRes private const val CONTEXT_MENU_RES = R.menu.ddu_chooser_context_menu
    }
}

