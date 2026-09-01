package dev.basri.android.nobs_launcher.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import dev.basri.android.nobs_launcher.R
import dev.basri.android.nobs_launcher.data.AppCatalog
import dev.basri.android.nobs_launcher.data.LayoutStore
import dev.basri.android.nobs_launcher.databinding.ActivitySettingsBinding
import dev.basri.android.nobs_launcher.model.LauncherConfig
import dev.basri.android.nobs_launcher.model.LauncherConfigPolicy

class SettingsActivity : Activity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: LayoutStore
    private lateinit var adapter: ManageAppsAdapter
    private lateinit var workingConfig: LauncherConfig
    private var isFirstRun = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = LayoutStore(this)
        workingConfig = store.load()
        isFirstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, !workingConfig.firstRunComplete)

        binding.welcomeText.setText(workingConfig.welcomeText)
        binding.wifiLabel.setText(workingConfig.wifiLabel)
        binding.locationLabel.setText(workingConfig.locationLabel)
        binding.showLocation.isChecked = workingConfig.showLocation
        binding.showVpnStatus.isChecked = workingConfig.showVpnStatus
        binding.showSystemStats.isChecked = workingConfig.showSystemStats

        adapter = ManageAppsAdapter { app, visible ->
            workingConfig = LauncherConfigPolicy.setVisible(
                workingConfig,
                app.packageName,
                visible,
            )
            adapter.updateSelection(workingConfig.selectedPackages)
        }
        binding.availableApps.layoutManager = LinearLayoutManager(this)
        binding.availableApps.adapter = adapter
        adapter.submit(AppCatalog(this).loadApps(), workingConfig.selectedPackages)

        binding.openAndroidSettings.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .onFailure {
                    Toast.makeText(this, R.string.unable_to_open_settings, Toast.LENGTH_SHORT).show()
                }
        }
        binding.save.setOnClickListener { saveAndFinish() }
    }

    @Deprecated("Deprecated in Android; required for API 23 TV compatibility")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!isFirstRun) {
            super.onBackPressed()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun saveAndFinish() {
        val saved = workingConfig.copy(
            firstRunComplete = true,
            wifiLabel = binding.wifiLabel.text.toString(),
            locationLabel = binding.locationLabel.text.toString(),
            welcomeText = binding.welcomeText.text.toString(),
            showLocation = binding.showLocation.isChecked,
            showVpnStatus = binding.showVpnStatus.isChecked,
            showSystemStats = binding.showSystemStats.isChecked,
        )
        store.save(saved)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    companion object {
        const val EXTRA_FIRST_RUN = "first_run"
    }
}
