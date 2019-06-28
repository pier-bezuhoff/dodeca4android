package com.pierbezuhoff.dodeca.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.viewModelScope
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.Options
import com.pierbezuhoff.dodeca.databinding.ActivityNewmainBinding
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.dodecaview.DodecaViewActivity
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManagerFactory
import kotlinx.coroutines.launch
import org.jetbrains.anko.defaultSharedPreferences

class NewMainActivity : AppCompatActivity()
{
    private val optionsManager by lazy {
        OptionsManager(defaultSharedPreferences)
    }
    private val factory by lazy {
        DodecaAndroidViewModelWithOptionsManagerFactory(application, optionsManager)
    }
    private val viewModel by lazy {
        ViewModelProviders.of(this, factory).get(NewMainViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: migrate to OptionsViewModel
        Options(resources).init() // init options.* and values.*
        val binding: ActivityNewmainBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_newmain)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        viewModel.viewModelScope.launch {
            if (viewModel.shouldUpgrade()) {
                viewModel.doUpgrade()
            }
            val intent = Intent(this@NewMainActivity, DodecaViewActivity::class.java)
            getUriFromImplicitIntent()?.let { uri: Uri ->
                intent.putExtra("ddu_uri", uri)
            }
            startActivity(intent)
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

    companion object {
        private const val TAG = "NewMainActivity"
        @Suppress("unused")
        const val LIMITED_VERSION = false
    }
}
