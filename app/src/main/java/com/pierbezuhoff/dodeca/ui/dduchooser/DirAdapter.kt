package com.pierbezuhoff.dodeca.ui.dduchooser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.recyclerview.widget.RecyclerView
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.utils.Connection
import java.io.File

class DirAdapter(private val dirs: List<File>) : MetaRecyclerViewAdapter<DirAdapter.DirViewHolder>() {
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
        val dir = dirs[position]
        with(holder) {
            view.findViewById<TextView>(R.id.dir_name)
                .text = dir.name
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

    override fun getItemCount(): Int =
        dirs.size

    companion object {
        private const val TAG = "DduFileAdapter"
        @MenuRes private const val CONTEXT_MENU_RES = R.menu.ddu_chooser_dir_context_menu
    }
}