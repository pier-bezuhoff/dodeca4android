package com.pierbezuhoff.dodeca.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.Options
import com.pierbezuhoff.dodeca.databinding.ActivityMainBinding
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.dodecaedit.DodecaEditActivity
import com.pierbezuhoff.dodeca.ui.dodecaview.DodecaViewActivity
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManagerFactory
import kotlinx.coroutines.launch
import org.jetbrains.anko.defaultSharedPreferences

class MainActivity : AppCompatActivity()
{
    private val optionsManager by lazy {
        OptionsManager(defaultSharedPreferences)
    }
    private val factory by lazy {
        DodecaAndroidViewModelWithOptionsManagerFactory(application, optionsManager)
    }
    private val viewModel by lazy {
        ViewModelProvider(this, factory).get(MainViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: migrate to OptionsViewModel
        Options(resources).init() // init options.* and values.*
        val binding: ActivityMainBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        viewModel.viewModelScope.launch {
            if (viewModel.shouldUpgrade()) {
                viewModel.doUpgrade()
            }
            startDodecaViewActivity()
        }
    }

    private fun getUriFromImplicitIntent(): Uri? =
        if (intent.action == Intent.ACTION_VIEW && (intent.type == null ||
                intent.type?.endsWith("ddu", ignoreCase = true) == true ||
                intent.data?.path?.endsWith(".ddu", ignoreCase = true) == true)
        )
            intent.data
        else
            null

    private fun startDodecaViewActivity() {
        val intent =
            if (START_IN_EDITOR)
                Intent(this@MainActivity, DodecaEditActivity::class.java)
            else
                Intent(this@MainActivity, DodecaViewActivity::class.java)
        getUriFromImplicitIntent()?.let { uri: Uri ->
            intent.putExtra("ddu_uri", uri)
        }
        startActivity(intent)
    }

    companion object {
        private const val TAG = "MainActivity"
        const val LIMITED_VERSION = true

        private const val START_IN_EDITOR: Boolean = false
    }
}
