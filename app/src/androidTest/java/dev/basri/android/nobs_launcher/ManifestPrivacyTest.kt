package dev.basri.android.nobs_launcher

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.basri.android.nobs_launcher.ui.HomeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestPrivacyTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val packageManager = context.packageManager

    @Test
    fun productionPackageHasOnlyNetworkStatePermission() {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }

        assertEquals(
            setOf(Manifest.permission.ACCESS_NETWORK_STATE),
            packageInfo.requestedPermissions.orEmpty().toSet(),
        )
    }

    @Test
    fun backupIsDisabled() {
        @Suppress("DEPRECATION")
        val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(0, applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun packageIdentityAndHomeIntentUseNoBullshitLauncher() {
        assertEquals("dev.basri.android.nobs_launcher.debug", context.packageName)
        assertEquals(
            "No bullshit launcher",
            packageManager.getApplicationLabel(context.applicationInfo).toString(),
        )
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val homeActivities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

        assertTrue(
            homeActivities.any {
                it.activityInfo.packageName == context.packageName &&
                    it.activityInfo.name == HomeActivity::class.java.name
            },
        )
    }
}
