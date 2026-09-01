package dev.basri.android.nobs_launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import java.util.Locale

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
}

class AppCatalog(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun loadApps(): List<AppCandidate> {
        val candidates = query(LaunchKind.TV) + query(LaunchKind.MOBILE)
        return AppCatalogPolicy.select(candidates, appContext.packageName)
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

    private fun query(kind: LaunchKind): List<AppCandidate> {
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
            AppCandidate(
                packageName = activityInfo.packageName,
                label = label,
                kind = kind,
                activityName = activityInfo.name,
                artwork = loadArtwork(resolveInfo),
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
}
