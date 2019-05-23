package com.pierbezuhoff.dodeca.models

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent

typealias ConnectionOutput = Connection<*>.Output

class Connection<ListenerInterface> {
    private var listener: ListenerInterface? = null
    val subscription: Subscription = Subscription()

    fun send(act: ListenerInterface.() -> Unit) {
        listener?.act()
    }

    inner class Subscription {
        fun subscribeFrom(listener: ListenerInterface): Output {
            this@Connection.listener = listener
            return Output()
        }
    }

    inner class Output {
        fun unsubscribe() {
            listener = null
        }
    }
}

fun <T> Connection<T>.Subscription.subsribeFromLifecycleOwner(
    listener: T
): Connection<T>.Output where T : LifecycleOwner =
    subscribeFrom(listener).also { output ->
        listener.lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun onDestroy() {
                output.unsubscribe()
            }
        })
    }

class Sender {
    interface Listener { fun onSend(); fun onSendStaff(s: String, i: Int) }
    private val connection = Connection<Listener>()
    val subscription = connection.subscription

    fun run() {
        connection.send { onSend() }
    }
}

class Receiver : Sender.Listener {
    private lateinit var output: Connection<*>.Output
    fun receive(sender: Sender) {
        output = sender.subscription.subscribeFrom(this)
    }
    override fun onSend() {
        print("received")
    }

    override fun onSendStaff(s: String, i: Int) {
        print("received $s $i")
    }

    fun onDestroy() {
        output.unsubscribe()
    }
}
