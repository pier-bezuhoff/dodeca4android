package com.pierbezuhoff.dodeca.ui.dduchooser

import androidx.recyclerview.widget.RecyclerView

/** Witness of RecyclerView.Adapter<VH> => DeltaHandler */
abstract class MetaRecyclerViewAdapter<VH : RecyclerView.ViewHolder>
    : RecyclerView.Adapter<VH>()
    , DeltaHandler {
    override fun updateAll() = notifyDataSetChanged()
    override fun update(position: Int) = notifyItemChanged(position)
    override fun add(position: Int) = notifyItemInserted(position)
    override fun removeAt(position: Int) = notifyItemRemoved(position)
    override fun move(fromPosition: Int, toPosition: Int) = notifyItemMoved(fromPosition, toPosition)
}

interface DeltaHandler {
    fun updateAll()
    fun update(position: Int)
    fun add(position: Int)
    fun removeAt(position: Int)
    fun move(fromPosition: Int, toPosition: Int)
}

/** Simultaneously do operation on [list] and dispath it to [deltaHandler] */
class DeltaList<T>(
    private val list: MutableList<T>,
    private val deltaHandler: DeltaHandler
) {
    fun updateAll() {
        deltaHandler.updateAll()
    }
    fun update(position: Int, item: T) {
        list[position] = item
        deltaHandler.update(position)
    }
    fun update(oldItem: T, item: T) {
        val position = list.indexOf(oldItem)
        list[position] = item
        deltaHandler.update(position)
    }
    fun add(position: Int, item: T) {
        list.add(position, item)
        deltaHandler.add(position)
    }
    fun add(item: T) {
        val position = list.size
        list.add(item)
        deltaHandler.add(position)
    }
    fun addBefore(item: T, newItem: T) =
        add(list.indexOf(item), newItem)
    fun addAfter(item: T, newItem: T) =
        add(list.indexOf(item) + 1, newItem)
    fun remove(item: T) {
        val position = list.indexOf(item)
        list.removeAt(position)
        deltaHandler.removeAt(position)
    }
}
