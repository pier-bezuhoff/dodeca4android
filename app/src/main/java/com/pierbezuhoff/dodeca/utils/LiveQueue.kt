package com.pierbezuhoff.dodeca.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.LinkedList

/* LiveData that remembers all unobserved changes. */
open class LiveQueue<T> {
    private val liveQueue: MutableLiveData<LinkedList<T>> = MutableLiveData(LinkedList(emptyList()))
    protected fun _post(value: T) {
        val queue = liveQueue.value
        if (queue == null)
            liveQueue.value = LinkedList(listOf(value))
        else
            queue.add(value)
    }

    fun observe(owner: LifecycleOwner, observer: Observer<T>) {
        liveQueue.value?.forEach { observer.onChanged(it) }
        liveQueue.value = LinkedList(emptyList())
        liveQueue.observe(owner, Observer { queue ->
            queue.forEach { observer.onChanged(it) }
            liveQueue.value = LinkedList(emptyList())
        })
    }
}

class MutableLiveQueue<T> : LiveQueue<T>() {
    fun post(value: T) = _post(value)
}