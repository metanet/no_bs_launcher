package dev.basri.android.nobs_launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import dev.basri.android.nobs_launcher.R
import dev.basri.android.nobs_launcher.data.AppCandidate
import dev.basri.android.nobs_launcher.data.AppCatalog
import dev.basri.android.nobs_launcher.data.LayoutStore
import dev.basri.android.nobs_launcher.databinding.ActivityHomeBinding
import dev.basri.android.nobs_launcher.model.LauncherConfigPolicy
import dev.basri.android.nobs_launcher.model.HomeAppSectionsPolicy
import dev.basri.android.nobs_launcher.stats.SystemStatsDisplay
import dev.basri.android.nobs_launcher.stats.SystemStatsMonitor
import dev.basri.android.nobs_launcher.status.VpnStatusMonitor
import dev.basri.android.nobs_launcher.time.ClockController

class HomeActivity : Activity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var store: LayoutStore
    private lateinit var catalog: AppCatalog
    private lateinit var adapter: AppGridAdapter
    private lateinit var clockController: ClockController
    private lateinit var vpnStatusMonitor: VpnStatusMonitor
    private lateinit var systemStatsMonitor: SystemStatsMonitor
    private var favoriteApps = listOf<AppCandidate>()
    private var remainingApps = listOf<AppCandidate>()
    private var moveSnapshot: List<String>? = null
    private var movingPackage: String? = null
    private var swallowKeyUp: Int? = null
    private var showVpnStatus = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideLaunchError = Runnable { binding.launchError.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = LayoutStore(this)
        catalog = AppCatalog(this)
        adapter = AppGridAdapter(
            onOpen = ::openApp,
            onLongPress = ::showAppActions,
        )
        binding.appGrid.layoutManager = GridLayoutManager(this, GRID_COLUMNS).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = adapter.spanSizeAt(position)
            }
        }
        binding.appGrid.adapter = adapter
        binding.settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        clockController = ClockController(this, binding.clock, binding.date)
        vpnStatusMonitor = VpnStatusMonitor(this) { label ->
            binding.vpnStatus.text = label.orEmpty()
            binding.vpnStatus.visibility = if (showVpnStatus && label != null) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        systemStatsMonitor = SystemStatsMonitor(this, ::bindSystemStats)
    }

    override fun onStart() {
        super.onStart()
        clockController.start()
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        val config = store.load()
        if (!config.firstRunComplete) {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_FIRST_RUN, true),
            )
            return
        }
        bindHome()
    }

    override fun onStop() {
        mainHandler.removeCallbacks(hideLaunchError)
        systemStatsMonitor.stop()
        vpnStatusMonitor.stop()
        clockController.stop()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == swallowKeyUp) {
            swallowKeyUp = null
            return true
        }
        if (moveSnapshot != null && event.action == KeyEvent.ACTION_UP && isMoveKey(event.keyCode)) {
            return true
        }
        if (moveSnapshot != null && event.action == KeyEvent.ACTION_DOWN) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> moveByDirection(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> moveByDirection(1)
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                -> true
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> {
                    swallowKeyUp = event.keyCode
                    saveMoveMode()
                    true
                }
                KeyEvent.KEYCODE_BACK -> {
                    swallowKeyUp = event.keyCode
                    cancelMoveMode()
                    true
                }
                else -> super.dispatchKeyEvent(event)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Android; required for API 23 TV compatibility")
    override fun onBackPressed() {
        if (moveSnapshot != null) {
            cancelMoveMode()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun bindHome(preferredFocusPackage: String? = currentFocusedPackage()) {
        val catalogApps = catalog.loadApps()
        val config = store.load()
        val normalized = LauncherConfigPolicy.normalize(
            config,
            catalogApps.mapTo(mutableSetOf(), AppCandidate::packageName),
        )
        if (normalized != config) store.save(normalized)

        val sections = HomeAppSectionsPolicy.compose(catalogApps, normalized.selectedPackages)
        favoriteApps = sections.favorites
        remainingApps = sections.remaining
        adapter.submitSections(favoriteApps, remainingApps)
        binding.emptyState.visibility = if (catalogApps.isEmpty()) View.VISIBLE else View.GONE
        binding.welcome.text = normalized.welcomeText
        binding.welcome.visibility = if (normalized.welcomeText.isBlank()) View.GONE else View.VISIBLE
        binding.wifi.text = normalized.wifiLabel
        binding.wifi.visibility = if (normalized.wifiLabel.isBlank()) View.GONE else View.VISIBLE
        binding.location.text = normalized.locationLabel
        binding.location.visibility = if (normalized.showLocation && normalized.locationLabel.isNotBlank()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        showVpnStatus = normalized.showVpnStatus
        if (normalized.showVpnStatus) {
            vpnStatusMonitor.start()
        } else {
            vpnStatusMonitor.stop()
            binding.vpnStatus.visibility = View.GONE
        }
        if (normalized.showSystemStats) {
            binding.systemStatsPanel.visibility = View.VISIBLE
            systemStatsMonitor.start()
        } else {
            systemStatsMonitor.stop()
            binding.systemStatsPanel.visibility = View.GONE
        }

        val focusIndex = preferredFocusPackage
            ?.let(adapter::positionOfPackage)
            ?.takeIf { it >= 0 }
        binding.appGrid.post {
            if (focusIndex != null) {
                binding.appGrid.findViewHolderForAdapterPosition(focusIndex)?.itemView?.requestFocus()
                    ?: binding.appGrid.scrollToPosition(focusIndex)
            } else if (catalogApps.isNotEmpty()) {
                binding.appGrid.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            } else {
                binding.settings.requestFocus()
            }
        }
    }

    private fun bindSystemStats(stats: SystemStatsDisplay) {
        binding.memoryStats.text = getString(R.string.memory_stats, stats.memory)
        binding.cpuStats.text = getString(R.string.cpu_stats, stats.cpu)
        binding.storageStats.text = getString(R.string.storage_stats, stats.storage)
        binding.networkStats.text = getString(R.string.network_stats, stats.network)
    }

    private fun openApp(app: AppCandidate) {
        if (moveSnapshot != null) return
        runCatching { startActivity(catalog.launchIntent(app)) }
            .onFailure { showLaunchError(app.label) }
    }

    private fun showLaunchError(label: String) {
        binding.launchError.text = getString(R.string.unable_to_open, label)
        binding.launchError.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideLaunchError)
        mainHandler.postDelayed(hideLaunchError, LAUNCH_ERROR_DURATION_MS)
    }

    private fun showAppActions(app: AppCandidate, favorite: Boolean) {
        if (moveSnapshot != null) return
        val actions = if (favorite) {
            listOf(AppAction.MOVE, AppAction.REMOVE_FAVORITE, AppAction.UNINSTALL)
        } else {
            listOf(AppAction.ADD_FAVORITE, AppAction.UNINSTALL)
        }
        val labels = actions.map { action -> getString(action.labelResource) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(labels) { _, index ->
                when (actions[index]) {
                    AppAction.ADD_FAVORITE -> setFavorite(app, favorite = true)
                    AppAction.MOVE -> startMoveMode(app)
                    AppAction.REMOVE_FAVORITE -> setFavorite(app, favorite = false)
                    AppAction.UNINSTALL -> uninstallApp(app)
                }
            }
            .show()
    }

    private fun setFavorite(app: AppCandidate, favorite: Boolean) {
        val updated = LauncherConfigPolicy.setVisible(
            config = store.load(),
            packageName = app.packageName,
            visible = favorite,
        )
        store.save(updated)
        bindHome(app.packageName)
    }

    private fun uninstallApp(app: AppCandidate) {
        runCatching {
            startActivity(
                Intent(
                    Intent.ACTION_DELETE,
                    Uri.parse("package:${app.packageName}"),
                ),
            )
        }.onFailure {
            showActionError(getString(R.string.unable_to_uninstall, app.label))
        }
    }

    private fun showActionError(message: String) {
        binding.launchError.text = message
        binding.launchError.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideLaunchError)
        mainHandler.postDelayed(hideLaunchError, LAUNCH_ERROR_DURATION_MS)
    }

    private fun startMoveMode(app: AppCandidate) {
        if (moveSnapshot != null) return
        moveSnapshot = favoriteApps.map(AppCandidate::packageName)
        movingPackage = app.packageName
        adapter.setMovingPackage(app.packageName)
        binding.moveModeHint.visibility = View.VISIBLE
    }

    private fun moveByDirection(delta: Int): Boolean {
        val packageName = movingPackage ?: return true
        val fromIndex = favoriteApps.indexOfFirst { it.packageName == packageName }
        if (fromIndex < 0) return true
        val toIndex = fromIndex + delta
        if (toIndex !in favoriteApps.indices) return true

        val reorderedPackages = LauncherConfigPolicy.move(
            favoriteApps.map(AppCandidate::packageName),
            fromIndex,
            toIndex,
        )
        val byPackage = favoriteApps.associateBy(AppCandidate::packageName)
        favoriteApps = reorderedPackages.mapNotNull(byPackage::get)
        adapter.submitSections(favoriteApps, remainingApps)
        adapter.setMovingPackage(packageName)
        requestTileFocus(toIndex)
        return true
    }

    private fun saveMoveMode() {
        val config = store.load().copy(
            selectedPackages = favoriteApps.map(AppCandidate::packageName),
        )
        store.save(config)
        finishMoveMode()
    }

    private fun cancelMoveMode() {
        val originalPackages = moveSnapshot.orEmpty()
        val byPackage = favoriteApps.associateBy(AppCandidate::packageName)
        favoriteApps = originalPackages.mapNotNull(byPackage::get)
        adapter.submitSections(favoriteApps, remainingApps)
        val focusIndex = favoriteApps.indexOfFirst { it.packageName == movingPackage }
        finishMoveMode()
        if (focusIndex >= 0) requestTileFocus(focusIndex)
    }

    private fun finishMoveMode() {
        moveSnapshot = null
        movingPackage = null
        adapter.setMovingPackage(null)
        binding.moveModeHint.visibility = View.GONE
    }

    private fun requestTileFocus(position: Int) {
        binding.appGrid.post {
            binding.appGrid.scrollToPosition(position)
            binding.appGrid.post {
                binding.appGrid.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }
        }
    }

    private fun currentFocusedPackage(): String? {
        val holder = binding.appGrid.findContainingViewHolder(currentFocus ?: return null)
            ?: return null
        return adapter.packageAt(holder.bindingAdapterPosition)
    }

    private fun isMoveKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
        keyCode == KeyEvent.KEYCODE_DPAD_UP ||
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
        keyCode == KeyEvent.KEYCODE_BACK

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

    private companion object {
        const val GRID_COLUMNS = 4
        const val LAUNCH_ERROR_DURATION_MS = 4_000L
    }

    private enum class AppAction(val labelResource: Int) {
        ADD_FAVORITE(R.string.add_to_favorites),
        MOVE(R.string.move),
        REMOVE_FAVORITE(R.string.remove_from_favorites),
        UNINSTALL(R.string.uninstall_app),
    }
}
