package com.pierbezuhoff.dodeca.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pierbezuhoff.dodeca.BuildConfig
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.ui.meta.DodecaAndroidViewModelWithOptionsManager
import com.pierbezuhoff.dodeca.utils.dduDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class MainViewModel(
    application: Application,
    optionsManager: OptionsManager
) : DodecaAndroidViewModelWithOptionsManager(application, optionsManager) {
    private val _status: MutableLiveData<String> = MutableLiveData("...")
    private val _loading: MutableLiveData<Boolean> = MutableLiveData(false)
    val status: LiveData<String> = _status
    val loading: LiveData<Boolean> = _loading

    fun shouldUpgrade(): Boolean {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val oldVersionCode = optionsManager.fetched(options.versionCode)
        return oldVersionCode != currentVersionCode
    }

    /** Upgrade ddu assets */
    suspend fun doUpgrade() {
        val currentVersionCode = BuildConfig.VERSION_CODE
        val oldVersionCode = optionsManager.fetched(options.versionCode)
        require(oldVersionCode != currentVersionCode) { "Nothing to upgrade" }
        val upgrading: Boolean = oldVersionCode < currentVersionCode
        val upgradingOrDegrading: String = if (upgrading) "Upgrading" else "Degrading"
        val currentVersionName: String = BuildConfig.VERSION_NAME
        val versionCodeChange = "$oldVersionCode -> $currentVersionCode"
        Log.i(TAG,"$upgradingOrDegrading to $currentVersionName ($versionCodeChange)")
        optionsManager.set(options.versionCode, currentVersionCode)
        onUpgrade()
    }

    private suspend fun onUpgrade() {
        // extracting assets
        val targetDir = context.dduDir
        withContext(Dispatchers.IO) {
            _loading.postValue(true)
            try {
                if (!targetDir.exists()) {
                    Log.i(TAG, "Extracting all assets into $targetDir")
                    _status.postValue(context.getString(R.string.extracting_assets_status, targetDir))
                    dduFileService.extractDduAssets(targetDir)
                } else { // try to export new ddus
                    Log.i(TAG, "Adding new ddu assets into $targetDir")
                    _status.postValue(context.getString(R.string.adding_assets_status, targetDir))
                    dduFileService.extractDduAssets(targetDir, onlyNew = true)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                _loading.postValue(false)
            }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
