package com.pierbezuhoff.dodeca.ui.dodecashow

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.databinding.ActivityDodecaShowBinding
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManagerFactory
import kotlinx.android.synthetic.main.activity_dodeca_show.*
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File

class DodecaShowActivity : AppCompatActivity() {
    private val optionsManager by lazy {
        OptionsManager(defaultSharedPreferences)
    }
    private val factory by lazy {
        DodecaAndroidViewModelWithOptionsManagerFactory(application, optionsManager)
    }
    private val viewModel by lazy {
        ViewModelProviders.of(this, factory).get(DodecaShowViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        val binding: ActivityDodecaShowBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_dodeca_show)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        setSupportActionBar(dodeca_show_toolbar)
        supportActionBar?.title = "x.ddu"
//        TODO("setup actionBar (title, menu, listener)")
        dodeca_show_view.inheritLifecycleOf(this)
        // FIX: do not pass empty dirs
        (intent.getSerializableExtra("dir") as File?)?.also { dir: File ->
            viewModel.setInitialTargetDir(dir)
            (intent.getSerializableExtra("ddu_file") as File?)?.also { file ->
                viewModel.setInitialTargetFile(file)
            }
        }
        viewModel.createRing()
    }

    private fun setupWindow() {
        window.decorView.apply {
            systemUiVisibility = IMMERSIVE_UI_VISIBILITY
            setOnSystemUiVisibilityChangeListener {
                if ((it and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                    systemUiVisibility =
                        IMMERSIVE_UI_VISIBILITY
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isSet = true
        when (item.itemId) {
            R.id.back -> Log.i(TAG, "back")
            else -> isSet = false
        }
        return isSet || super.onOptionsItemSelected(item)
    }

    private fun chooseFile() {
//        TODO("viewModel.file -> intent -> DodecaViewActivity")
        val intent = Intent()
        intent.putExtra("ddu_file", viewModel.file)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        viewModel.resume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.pause()
    }

    companion object {
        private const val TAG = "DodecaShowActivity"
        private const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
