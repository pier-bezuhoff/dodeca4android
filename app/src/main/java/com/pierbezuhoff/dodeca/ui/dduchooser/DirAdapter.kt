package com.pierbezuhoff.dodeca.ui.dduchooser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.utils.Connection
import com.pierbezuhoff.dodeca.utils.fileName
import kotlinx.android.synthetic.main.dir_row.view.*
import java.io.File

class DirAdapter
    : PagedListAdapter<File, DirAdapter.DirViewHolder>(DIFF_CALLBACK) {
    interface DirChangeListener { fun onDirChanged(dir: File) }

    private val dirChangeConnection = Connection<DirChangeListener>()
    val dirChangeSubscription = dirChangeConnection.subscription
    private val contextMenuConnection = Connection<ContextMenuManager>()
    val contextMenuSubscription = contextMenuConnection.subscription

    class DirViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.dir_row, parent, false)
        return DirViewHolder(view)
    }

    override fun onBindViewHolder(holder: DirViewHolder, position: Int) {
        val dir = getItem(position)
        dir?.let {
            with(holder) {
                view.dir_name.text = dir.name
                view.setOnClickListener {
                    dirChangeConnection.send { onDirChanged(dir) }
                }
                contextMenuConnection.send {
                    registerForContextMenu(
                        view,
                        menuRes = CONTEXT_MENU_RES,
                        contextMenuSource = ContextMenuSource.Dir(dir)
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "DduFileAdapter"
        @MenuRes private const val CONTEXT_MENU_RES = R.menu.ddu_chooser_dir_context_menu
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem.fileName == newItem.fileName
            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean =
                oldItem == newItem
        }
    }
}

