package dev.basri.android.nobs_launcher.ui

import android.Manifest
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
import dev.basri.android.nobs_launcher.data.CatalogRequest
import dev.basri.android.nobs_launcher.data.FaviconRepository
import dev.basri.android.nobs_launcher.data.LayoutStore
import dev.basri.android.nobs_launcher.data.WebShortcutService
import dev.basri.android.nobs_launcher.databinding.ActivityHomeBinding
import dev.basri.android.nobs_launcher.model.HomeItem
import dev.basri.android.nobs_launcher.model.HomeItemSectionsPolicy
import dev.basri.android.nobs_launcher.model.LauncherConfig
import dev.basri.android.nobs_launcher.model.LauncherConfigPolicy
import dev.basri.android.nobs_launcher.model.WebShortcut
import dev.basri.android.nobs_launcher.stats.SystemStatsDisplay
import dev.basri.android.nobs_launcher.stats.SystemStatsMonitor
import dev.basri.android.nobs_launcher.status.SystemLabelPolicy
import dev.basri.android.nobs_launcher.status.SystemLabelReader
import dev.basri.android.nobs_launcher.status.VpnStatusMonitor
import dev.basri.android.nobs_launcher.time.ClockController

class HomeActivity : Activity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var store: LayoutStore
    private lateinit var catalog: AppCatalog
    private lateinit var adapter: AppGridAdapter
    private lateinit var favicons: FaviconRepository
    private lateinit var shortcutService: WebShortcutService
    private lateinit var clockController: ClockController
    private lateinit var vpnStatusMonitor: VpnStatusMonitor
    private lateinit var systemStatsMonitor: SystemStatsMonitor
    private lateinit var systemLabelReader: SystemLabelReader
    private var favoriteItems = listOf<HomeItem>()
    private var remainingItems = listOf<HomeItem>()
    private var moveSnapshot: List<String>? = null
    private var movingItemId: String? = null
    private var swallowKeyUp: Int? = null
    private var showVpnStatus = false
    private var catalogRequest: CatalogRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideLaunchError = Runnable { binding.launchError.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = LayoutStore(this)
        systemLabelReader = SystemLabelReader(this)
        catalog = AppCatalog.shared(this)
        favicons = FaviconRepository(this)
        shortcutService = WebShortcutService(store, favicons)
        adapter = AppGridAdapter(
            favicons = favicons,
            onOpen = ::openItem,
            onLongPress = ::showItemActions,
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
        catalogRequest?.cancel()
        catalogRequest = null
        mainHandler.removeCallbacks(hideLaunchError)
        systemStatsMonitor.stop()
        vpnStatusMonitor.stop()
        clockController.stop()
        super.onStop()
    }

    override fun onDestroy() {
        favicons.close()
        super.onDestroy()
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
        if (moveSnapshot != null) cancelMoveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    private fun bindHome(preferredFocusItemId: String? = currentFocusedItemId()) {
        var normalized: LauncherConfig? = null
        store.update { current ->
            LauncherConfigPolicy.normalize(current).also { normalized = it }
        }
        val currentConfig = normalized ?: LauncherConfigPolicy.normalize(store.load())
        catalogRequest?.cancel()
        catalogRequest = catalog.loadApps { catalogApps ->
            if (!isFinishing && !isDestroyed) {
                renderHome(catalogApps, currentConfig, preferredFocusItemId)
            }
        }
    }

    private fun renderHome(
        catalogApps: List<AppCandidate>,
        currentConfig: LauncherConfig,
        preferredFocusItemId: String?,
    ) {
        val allItems = catalogApps.map(HomeItem::App) + currentConfig.shortcuts.map(HomeItem::Web)
        val sections = HomeItemSectionsPolicy.compose(allItems, currentConfig.favoriteItemIds)
        favoriteItems = sections.favorites
        remainingItems = sections.remaining
        adapter.submitSections(favoriteItems, remainingItems)
        binding.emptyState.visibility = if (allItems.isEmpty()) View.VISIBLE else View.GONE
        binding.welcome.text = currentConfig.welcomeText
        binding.welcome.visibility = if (currentConfig.welcomeText.isBlank()) View.GONE else View.VISIBLE
        val wifiName = SystemLabelPolicy.visibleWifiName(
            showWifiName = currentConfig.showWifiName,
            wifiName = systemLabelReader.wifiName(),
        )
        binding.wifi.text = wifiName.orEmpty()
        binding.wifi.visibility = if (wifiName != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.location.text = systemLabelReader.locationName()
            ?: getString(R.string.location_unavailable)
        binding.location.visibility = if (currentConfig.showLocation) View.VISIBLE else View.GONE
        requestWifiPermissionOnce(currentConfig)
        showVpnStatus = currentConfig.showVpnStatus
        if (currentConfig.showVpnStatus) {
            vpnStatusMonitor.start()
        } else {
            vpnStatusMonitor.stop()
            binding.vpnStatus.visibility = View.GONE
        }
        if (currentConfig.showSystemStats) {
            binding.systemStatsPanel.visibility = View.VISIBLE
            systemStatsMonitor.start()
        } else {
            systemStatsMonitor.stop()
            binding.systemStatsPanel.visibility = View.GONE
        }

        val focusIndex = preferredFocusItemId
            ?.let(adapter::positionOfItem)
            ?.takeIf { it >= 0 }
        binding.appGrid.post {
            if (focusIndex != null) {
                binding.appGrid.findViewHolderForAdapterPosition(focusIndex)?.itemView?.requestFocus()
                    ?: binding.appGrid.scrollToPosition(focusIndex)
            } else if (allItems.isNotEmpty()) {
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
        binding.networkIngressStats.text = getString(
            R.string.network_ingress_stats,
            stats.networkIngress,
        )
        binding.networkEgressStats.text = getString(
            R.string.network_egress_stats,
            stats.networkEgress,
        )
    }

    private fun requestWifiPermissionOnce(config: LauncherConfig) {
        if (
            !config.showWifiName ||
            SystemLabelReader.hasWifiNamePermission(this) ||
            store.hasRequestedWifiPermission()
        ) {
            return
        }
        if (store.markWifiPermissionRequested()) {
            requestPermissions(
                WIFI_NAME_PERMISSIONS,
                REQUEST_WIFI_NAME_PERMISSION,
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            requestCode == REQUEST_WIFI_NAME_PERMISSION &&
            SystemLabelReader.hasWifiNamePermission(this)
        ) {
            bindHome()
        }
    }

    private fun openItem(item: HomeItem) {
        if (moveSnapshot != null) return
        val launch = when (item) {
            is HomeItem.App -> catalog.launchIntent(item.candidate)
            is HomeItem.Web -> Intent(Intent.ACTION_VIEW, Uri.parse(item.shortcut.url))
        }
        runCatching { startActivity(launch) }
            .onFailure { showLaunchError(item.label) }
    }

    private fun showLaunchError(label: String) {
        showActionError(getString(R.string.unable_to_open, label))
    }

    private fun showItemActions(item: HomeItem, favorite: Boolean) {
        if (moveSnapshot != null) return
        val actions = when (item) {
            is HomeItem.App -> if (favorite) {
                listOf(HomeAction.MOVE, HomeAction.REMOVE_FAVORITE, HomeAction.UNINSTALL)
            } else {
                listOf(HomeAction.ADD_FAVORITE, HomeAction.UNINSTALL)
            }
            is HomeItem.Web -> if (favorite) {
                listOf(
                    HomeAction.MOVE,
                    HomeAction.REMOVE_FAVORITE,
                    HomeAction.EDIT_SHORTCUT,
                    HomeAction.REMOVE_SHORTCUT,
                )
            } else {
                listOf(
                    HomeAction.ADD_FAVORITE,
                    HomeAction.EDIT_SHORTCUT,
                    HomeAction.REMOVE_SHORTCUT,
                )
            }
        }
        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setItems(actions.map { getString(it.labelResource) }.toTypedArray()) { _, index ->
                when (actions[index]) {
                    HomeAction.ADD_FAVORITE -> setFavorite(item, favorite = true)
                    HomeAction.MOVE -> startMoveMode(item)
                    HomeAction.REMOVE_FAVORITE -> setFavorite(item, favorite = false)
                    HomeAction.UNINSTALL -> uninstallApp((item as HomeItem.App).candidate)
                    HomeAction.EDIT_SHORTCUT -> editShortcut((item as HomeItem.Web).shortcut)
                    HomeAction.REMOVE_SHORTCUT -> confirmShortcutRemoval(
                        (item as HomeItem.Web).shortcut,
                    )
                }
            }
            .show()
    }

    private fun setFavorite(item: HomeItem, favorite: Boolean) {
        if (store.update { current ->
                LauncherConfigPolicy.setFavorite(current, item.id, favorite)
            }
        ) {
            bindHome(item.id)
        } else {
            showActionError(getString(R.string.shortcut_save_failed))
        }
    }

    private fun uninstallApp(app: AppCandidate) {
        runCatching {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
        }.onFailure {
            showActionError(getString(R.string.unable_to_uninstall, app.label))
        }
    }

    private fun editShortcut(shortcut: WebShortcut) {
        startActivity(
            Intent(this, WebShortcutsActivity::class.java)
                .putExtra(WebShortcutsActivity.EXTRA_EDIT_SHORTCUT_ID, shortcut.uuid),
        )
    }

    private fun confirmShortcutRemoval(shortcut: WebShortcut) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.remove_shortcut)
            .setMessage(getString(R.string.confirm_remove_shortcut, shortcut.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove_shortcut) { _, _ ->
                if (shortcutService.remove(shortcut.uuid)) {
                    bindHome()
                } else {
                    showActionError(getString(R.string.shortcut_remove_failed))
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
        }
        dialog.show()
    }

    private fun showActionError(message: String) {
        binding.launchError.text = message
        binding.launchError.visibility = View.VISIBLE
        mainHandler.removeCallbacks(hideLaunchError)
        mainHandler.postDelayed(hideLaunchError, LAUNCH_ERROR_DURATION_MS)
    }

    private fun startMoveMode(item: HomeItem) {
        if (moveSnapshot != null) return
        moveSnapshot = favoriteItems.map(HomeItem::id)
        movingItemId = item.id
        adapter.setMovingItemId(item.id)
        binding.moveModeHint.visibility = View.VISIBLE
    }

    private fun moveByDirection(delta: Int): Boolean {
        val itemId = movingItemId ?: return true
        val fromIndex = favoriteItems.indexOfFirst { it.id == itemId }
        if (fromIndex < 0) return true
        val toIndex = fromIndex + delta
        if (toIndex !in favoriteItems.indices) return true

        val reorderedIds = LauncherConfigPolicy.move(
            favoriteItems.map(HomeItem::id),
            fromIndex,
            toIndex,
        )
        val byId = favoriteItems.associateBy(HomeItem::id)
        favoriteItems = reorderedIds.mapNotNull(byId::get)
        adapter.submitSections(favoriteItems, remainingItems)
        adapter.setMovingItemId(itemId)
        requestTileFocus(toIndex)
        return true
    }

    private fun saveMoveMode() {
        val favoriteItemIds = favoriteItems.map(HomeItem::id)
        if (!store.update { current -> current.copy(favoriteItemIds = favoriteItemIds) }) {
            showActionError(getString(R.string.shortcut_save_failed))
        }
        finishMoveMode()
    }

    private fun cancelMoveMode() {
        val originalIds = moveSnapshot.orEmpty()
        val byId = favoriteItems.associateBy(HomeItem::id)
        favoriteItems = originalIds.mapNotNull(byId::get)
        adapter.submitSections(favoriteItems, remainingItems)
        val focusIndex = favoriteItems.indexOfFirst { it.id == movingItemId }
        finishMoveMode()
        if (focusIndex >= 0) requestTileFocus(focusIndex)
    }

    private fun finishMoveMode() {
        moveSnapshot = null
        movingItemId = null
        adapter.setMovingItemId(null)
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

    private fun currentFocusedItemId(): String? {
        val holder = binding.appGrid.findContainingViewHolder(currentFocus ?: return null)
            ?: return null
        return adapter.itemIdAt(holder.bindingAdapterPosition)
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
        const val REQUEST_WIFI_NAME_PERMISSION = 1001
        val WIFI_NAME_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    private enum class HomeAction(val labelResource: Int) {
        ADD_FAVORITE(R.string.add_to_favorites),
        MOVE(R.string.move),
        REMOVE_FAVORITE(R.string.remove_from_favorites),
        UNINSTALL(R.string.uninstall_app),
        EDIT_SHORTCUT(R.string.edit_shortcut),
        REMOVE_SHORTCUT(R.string.remove_shortcut),
    }
}
