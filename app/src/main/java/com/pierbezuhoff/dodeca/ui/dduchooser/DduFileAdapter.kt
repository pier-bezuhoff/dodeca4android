package com.pierbezuhoff.dodeca.ui.dduchooser

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.databinding.DduItemBinding
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.utils.Connection
import com.pierbezuhoff.dodeca.utils.LifecycleInheritance
import com.pierbezuhoff.dodeca.utils.LifecycleInheritor
import com.pierbezuhoff.dodeca.utils.fileName
import java.io.File

class DduFileAdapter(
    private val files: List<File>,
    optionsManager: OptionsManager
)
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

    private val blankPreview: Bitmap = Ddu.createBlankPreview(optionsManager.values.previewSizePx)

    class DduFileViewHolder(val binding: DduItemBinding) : RecyclerView.ViewHolder(binding.root) {
        lateinit var file: File
    }

    fun findPositionOf(file: File): Int? {
        val ix = files.indexOf(file)
        return if (ix == -1) null else ix
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DduFileViewHolder {
        val binding = DduItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false
        )
        return DduFileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DduFileViewHolder, position: Int) {
        val file: File = files[position]
        with(holder.binding) {
            holder.file = file
            dduEntry.text = file.fileName.toString()
            dduPreview.setOnClickListener {
                fileChooserConnection.send { chooseFile(file) }
            }
            contextMenuConnection.send {
                registerForContextMenu(
                    dduPreviewLayout,
                    menuRes = CONTEXT_MENU_RES,
                    contextMenuSource = ContextMenuSource.DduFile(file)
                )
            }
            showProgressBar(holder)
            require(lifecycleInherited)
            File("")
            previewSupplierConnection.send {
                getPreviewOf(file)
            } ?.observe(this@DduFileAdapter) { (file: File, newBitmap: Bitmap) ->
                // NOTE: view holder may be recycled and re-bind-ed while we are waiting for the preview,
                //  so we tag it with [file]
                if (holder.file == file)
                    setPreview(holder, newBitmap)
            }
        }
    }

    private fun showProgressBar(holder: DduFileViewHolder) {
        holder.binding.dduPreview.apply {
            visibility = View.VISIBLE
            setImageBitmap(blankPreview)
        }
        holder.binding.previewProgress.visibility = View.VISIBLE
    }

    private fun setPreview(holder: DduFileViewHolder, bitmap: Bitmap) {
        holder.binding.dduPreview.apply {
            visibility = View.VISIBLE
            setImageBitmap(bitmap)
        }
        holder.binding.previewProgress.visibility = View.GONE
    }

    override fun getItemCount(): Int =
        files.size

    companion object {
        private const val TAG = "DduFileAdapter"
        @MenuRes private const val CONTEXT_MENU_RES = R.menu.ddu_chooser_context_menu
    }
}

