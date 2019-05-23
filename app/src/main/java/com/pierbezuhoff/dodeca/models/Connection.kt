package com.pierbezuhoff.dodeca.models

class Connection<T> {
    private var listener: T? = null
    val input: Input = Input()
    val subscription: Subscription = Subscription()

    inner class Subscription {
        fun subscribe(listener: T): Output {
            this@Connection.listener = listener
            return Output()
        }
    }

    inner class Output {
        fun unsubscribe() {
            listener = null
        }
    }

    inner class Input {
        fun send(act: T.() -> Unit) {
            listener?.act()
        }
    }
}

class Sender {
    interface Listener { fun onSend() }
    private val connection = Connection<Listener>()
    private val input = connection.input
    val subscription = connection.subscription

    fun run() {
        input.send { onSend() }
    }
}

class Receiver : Sender.Listener {
    private lateinit var output: Connection<*>.Output
    fun receive(sender: Sender) {
        output = sender.subscription.subscribe(this)
    }
    override fun onSend() {
        print("received")
    }

    fun onDestroy() {
        output.unsubscribe()
    }
}
