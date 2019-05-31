package com.pierbezuhoff.dodeca.ui.dduchooser

import android.graphics.Bitmap
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.observe
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.utils.Connection
import com.pierbezuhoff.dodeca.utils.fileName
import kotlinx.android.synthetic.main.ddu_item.view.*
import java.io.File

class DduFileAdapter
    : PagedListAdapter<File, DduFileAdapter.DduFileViewHolder>(DIFF_CALLBACK)
    , LifecycleOwner
{
    interface FileChooser { fun chooseFile(file: File) }
    interface ContextMenuManager {
        // activity.registerForContextMenu(view)
        fun registerViewForContextMenu(view: View)
        // activity.menuInflater.inflate(R.menu.ddu_chooser_context_menu, menu)
        fun inflateMenu(@MenuRes menuRes: Int, menu: Menu)
    }
    lateinit var model: DduChooserViewModel // inject
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    private val fileChooserConnection = Connection<FileChooser>()
    private val contextMenuConnection = Connection<ContextMenuManager>()
    val fileChooserSubscription = fileChooserConnection.subscription
    val contextMenuSubscription = contextMenuConnection.subscription
    class DduFileViewHolder(val view: View) : RecyclerView.ViewHolder(view)
    data class DduFileEntry(val file: File, val bitmap: Bitmap?)

    init {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun getLifecycle(): Lifecycle =
        lifecycleRegistry

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
                view.ddu_entry.text = file.fileName.toString()
                view.setOnClickListener {
                    fileChooserConnection.send { chooseFile(file) }
                }
                contextMenuConnection.send { registerViewForContextMenu(view) }
                view.setOnCreateContextMenuListener { menu, _, _ ->
                    model.dduContextMenuCreatorPosition = position
                    contextMenuConnection.send { inflateMenu(R.menu.ddu_chooser_context_menu, menu) }
                }
                setIsRecyclable(false)
                noPreview(holder)
                model.getPreviewOf(file)
                    .observe(this@DduFileAdapter) { newBitmap: Bitmap ->
                        setPreview(holder, newBitmap)
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

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        // NOTE: should be called only when activity destroys
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        Log.i(TAG, "onDetachedFromRecyclerView => Lifecycle.Event.ON_DESTROY")
    }

    companion object {
        private const val TAG = "DduFileAdapter"
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem.fileName == newItem.fileName
            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem == newItem
        }
    }
}
