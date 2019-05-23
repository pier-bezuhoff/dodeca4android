package com.pierbezuhoff.dodeca.ui

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

abstract class AppCompatActivityWithCoroutineContext : AppCompatActivity(), CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main
    private lateinit var job: Job

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
        job = Job()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }
}