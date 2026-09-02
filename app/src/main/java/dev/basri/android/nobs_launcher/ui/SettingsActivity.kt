package dev.basri.android.nobs_launcher.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import dev.basri.android.nobs_launcher.BuildConfig
import dev.basri.android.nobs_launcher.R
import dev.basri.android.nobs_launcher.data.AppCatalog
import dev.basri.android.nobs_launcher.data.LayoutStore
import dev.basri.android.nobs_launcher.databinding.ActivitySettingsBinding
import dev.basri.android.nobs_launcher.model.HomeItemId
import dev.basri.android.nobs_launcher.model.LauncherConfig
import dev.basri.android.nobs_launcher.model.LauncherConfigPolicy
import dev.basri.android.nobs_launcher.status.SystemLabelReader

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
        binding.showWifiName.isChecked = workingConfig.showWifiName
        binding.showLocation.isChecked = workingConfig.showLocation
        binding.showVpnStatus.isChecked = workingConfig.showVpnStatus
        binding.showSystemStats.isChecked = workingConfig.showSystemStats
        binding.showWifiName.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !SystemLabelReader.hasWifiNamePermission(this)) {
                store.markWifiPermissionRequested()
                requestPermissions(
                    WIFI_NAME_PERMISSIONS,
                    REQUEST_WIFI_NAME_PERMISSION,
                )
            }
        }

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
        binding.webShortcuts.setOnClickListener {
            startActivity(Intent(this, WebShortcutsActivity::class.java))
        }
        binding.buildInfo.setOnClickListener { showBuildInformation() }
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
        val selectedAppIds = workingConfig.favoriteItemIds
            .filter { HomeItemId.appPackage(it) != null }
        val selectedAppIdSet = selectedAppIds.toSet()
        val saved = store.update { current ->
            val mergedFavoriteIds = current.favoriteItemIds
                .filter { HomeItemId.webUuid(it) != null || it in selectedAppIdSet }
                .toMutableList()
                .apply {
                    selectedAppIds.forEach { itemId ->
                        if (itemId !in this) add(itemId)
                    }
            }
            current.copy(
                firstRunComplete = true,
                favoriteItemIds = mergedFavoriteIds,
                welcomeText = binding.welcomeText.text.toString(),
                showWifiName = binding.showWifiName.isChecked,
                showLocation = binding.showLocation.isChecked,
                showVpnStatus = binding.showVpnStatus.isChecked,
                showSystemStats = binding.showSystemStats.isChecked,
            )
        }
        if (saved) {
            finish()
        } else {
            Toast.makeText(this, R.string.shortcut_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBuildInformation() {
        val message = buildString {
            append(getString(R.string.build_hash, BuildConfig.BUILD_GIT_HASH))
            append('\n')
            append(getString(R.string.build_date, BuildConfig.BUILD_DATE_UTC))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.build_information)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.findViewById<TextView>(android.R.id.message)?.apply {
                gravity = Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
        }
        dialog.show()
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
        private const val REQUEST_WIFI_NAME_PERMISSION = 1001
        private val WIFI_NAME_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}
