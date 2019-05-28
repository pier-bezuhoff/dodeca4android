package com.pierbezuhoff.dodeca.ui.dodecaedit

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.databinding.ActivityDodecaEditBinding
import kotlinx.android.synthetic.main.activity_dodeca_edit.*

class DodecaEditActivity : AppCompatActivity() {

    private val model by lazy {
        ViewModelProviders.of(this).get(DodecaEditViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.apply {
            systemUiVisibility =
                IMMERSIVE_UI_VISIBILITY // FULLSCREEN_UI_VISIBILITY
            setOnSystemUiVisibilityChangeListener {
                if ((it and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                    systemUiVisibility =
                        IMMERSIVE_UI_VISIBILITY
            }
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        val binding = DataBindingUtil.setContentView<ActivityDodecaEditBinding>(this, R.layout.activity_dodeca_edit)
        binding.lifecycleOwner = this
        binding.model = model
        setSupportActionBar(edit_bar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    companion object {
        const val TAG = "DodecaEditActivity"
        const val IMMERSIVE_UI_VISIBILITY = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}
