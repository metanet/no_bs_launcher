package dev.basri.android.nobs_launcher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

enum class LaunchKind {
    TV,
    MOBILE,
}

data class AppCandidate(
    val packageName: String,
    val label: String,
    val kind: LaunchKind,
    val activityName: String,
    val artwork: Drawable? = null,
)

object AppCatalogPolicy {
    fun select(candidates: List<AppCandidate>, selfPackage: String): List<AppCandidate> = candidates
        .asSequence()
        .filterNot { it.packageName == selfPackage }
        .groupBy(AppCandidate::packageName)
        .values
        .map { samePackage ->
            samePackage.firstOrNull { it.kind == LaunchKind.TV } ?: samePackage.first()
        }
        .sortedWith(
            compareBy<AppCandidate> { it.label.lowercase(Locale.getDefault()) }
                .thenBy(AppCandidate::packageName),
        )

    fun selectAndLoadArtwork(
        candidates: List<AppCandidate>,
        selfPackage: String,
        artworkLoader: (AppCandidate) -> Drawable?,
    ): List<AppCandidate> = select(candidates, selfPackage).map { candidate ->
        candidate.copy(artwork = artworkLoader(candidate))
    }
}

class CatalogRequest internal constructor() {
    @Volatile
    private var canceled = false

    fun cancel() {
        canceled = true
    }

    internal fun deliver(apps: List<AppCandidate>, callback: (List<AppCandidate>) -> Unit) {
        if (!canceled) callback(apps)
    }
}

class AppCatalog private constructor(
    context: Context,
    private val executor: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "nobs-app-catalog").apply { isDaemon = true }
    },
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateLock = Any()
    private val pending = mutableListOf<Pair<CatalogRequest, (List<AppCandidate>) -> Unit>>()
    private var cachedApps: List<AppCandidate>? = null
    private var loading = false
    private var generation = 0L

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidate()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                packageChangeReceiver,
                filter,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(packageChangeReceiver, filter)
        }
    }

    fun loadApps(onLoaded: (List<AppCandidate>) -> Unit): CatalogRequest {
        val request = CatalogRequest()
        var cached: List<AppCandidate>? = null
        var shouldLoad = false
        synchronized(stateLock) {
            cached = cachedApps
            if (cached == null) {
                pending += request to onLoaded
                if (!loading) {
                    loading = true
                    shouldLoad = true
                }
            }
        }
        cached?.let { apps -> mainHandler.post { request.deliver(apps, onLoaded) } }
        if (shouldLoad) scheduleLoad()
        return request
    }

    fun loadAppsBlocking(): List<AppCandidate> = loadFreshApps().also { loaded ->
        if (loaded.isNotEmpty()) synchronized(stateLock) { cachedApps = loaded }
    }

    fun invalidate() {
        var shouldLoad = false
        synchronized(stateLock) {
            generation += 1
            cachedApps = null
            if (!loading && pending.isNotEmpty()) {
                loading = true
                shouldLoad = true
            }
        }
        if (shouldLoad) scheduleLoad()
    }

    private fun scheduleLoad() {
        val loadGeneration = synchronized(stateLock) { generation }
        executor.execute {
            val loaded = runCatching(::loadFreshApps).getOrDefault(emptyList())
            var callbacks = emptyList<Pair<CatalogRequest, (List<AppCandidate>) -> Unit>>()
            var reload = false
            synchronized(stateLock) {
                loading = false
                if (loadGeneration == generation) {
                    cachedApps = loaded.takeIf(List<AppCandidate>::isNotEmpty)
                    callbacks = pending.toList()
                    pending.clear()
                } else if (pending.isNotEmpty()) {
                    loading = true
                    reload = true
                }
            }
            callbacks.forEach { (request, callback) ->
                mainHandler.post { request.deliver(loaded, callback) }
            }
            if (reload) scheduleLoad()
        }
    }

    private fun loadFreshApps(): List<AppCandidate> {
        val resolved = query(LaunchKind.TV) + query(LaunchKind.MOBILE)
        val byKey = resolved.associateBy { entry -> entry.candidate.key() }
        return AppCatalogPolicy.selectAndLoadArtwork(
            candidates = resolved.map(ResolvedCandidate::candidate),
            selfPackage = appContext.packageName,
            artworkLoader = { candidate ->
                byKey[candidate.key()]?.resolveInfo?.let(::loadArtwork)
            },
        )
    }

    fun launchIntent(app: AppCandidate): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(
            if (app.kind == LaunchKind.TV) {
                Intent.CATEGORY_LEANBACK_LAUNCHER
            } else {
                Intent.CATEGORY_LAUNCHER
            },
        )
        .setComponent(ComponentName(app.packageName, app.activityName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun query(kind: LaunchKind): List<ResolvedCandidate> {
        val category = if (kind == LaunchKind.TV) {
            Intent.CATEGORY_LEANBACK_LAUNCHER
        } else {
            Intent.CATEGORY_LAUNCHER
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        return queryIntentActivities(intent).mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim()
                .orEmpty()
                .ifEmpty { activityInfo.packageName }
            ResolvedCandidate(
                candidate = AppCandidate(
                    packageName = activityInfo.packageName,
                    label = label,
                    kind = kind,
                    activityName = activityInfo.name,
                ),
                resolveInfo = resolveInfo,
            )
        }
    }

    private fun loadArtwork(resolveInfo: ResolveInfo): Drawable? = runCatching {
        resolveInfo.activityInfo.loadBanner(packageManager)
            ?: resolveInfo.loadIcon(packageManager)
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun queryIntentActivities(intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0L),
            )
        } else {
            packageManager.queryIntentActivities(intent, 0)
        }

    private data class ResolvedCandidate(
        val candidate: AppCandidate,
        val resolveInfo: ResolveInfo,
    )

    private fun AppCandidate.key(): String = "$packageName\u0000$activityName\u0000$kind"

    companion object {
        @Volatile
        private var sharedInstance: AppCatalog? = null

        fun shared(context: Context): AppCatalog = sharedInstance ?: synchronized(this) {
            sharedInstance ?: AppCatalog(context).also { sharedInstance = it }
        }
    }
}
