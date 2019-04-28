package com.pierbezuhoff.dodeca.utils

import java.util.Timer
import kotlin.concurrent.timerTask

class FlexibleTimer(val timeMilis: Long, private val action: () -> Unit) {
    private var timer: Timer? = null

    fun start() {
        timer?.cancel()
        timer = Timer().apply {
            schedule(timerTask { action() }, timeMilis)
        }
    }

    fun stop() {
        timer?.cancel()
        timer = null
    }
}