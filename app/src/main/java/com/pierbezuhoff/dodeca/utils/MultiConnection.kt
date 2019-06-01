package com.pierbezuhoff.dodeca.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import java.lang.ref.WeakReference

/** Connection with multiple receivers */
class MultiConnection<ListenerInterface> {
    private val listeners: MutableMap<Id, WeakReference<ListenerInterface>> =
        mutableMapOf()
    val subscription: Subscription = Subscription()

    fun send(act: ListenerInterface.() -> Unit) {
        listeners.values.removeAll { weakReference -> weakReference.get() == null }
        listeners.values.forEach { it.get()?.act() }
    }

    // Subscription to Connection is like LiveData to MutableLiveData
    inner class Subscription internal constructor() {
        fun subscribeFrom(listener: ListenerInterface): Output {
            val id = Id.generate()
            listeners[id] = WeakReference(listener)
            return Output(id)
        }
    }

    inner class Output internal constructor(private val id: Id) {
        fun unsubscribe() {
            listeners.remove(id)
        }

        fun unsubscribeOnDestroy(lifecycleOwner: LifecycleOwner) {
            lifecycleOwner.lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun onDestroy() { unsubscribe() }
            })
        }
    }

    internal class Id private constructor(val id: Int) {
        override fun hashCode(): Int =
            id

        override fun equals(other: Any?): Boolean =
            other is Id && other.id == id

        override fun toString(): String =
            "Id($id)"

        companion object {
            private var nextId: Int = 0

            fun generate(): Id =
                Id(nextId).also {
                    nextId++
                }
        }
    }
}