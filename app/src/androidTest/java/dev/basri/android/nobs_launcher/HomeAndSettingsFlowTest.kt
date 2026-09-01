package dev.basri.android.nobs_launcher

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import android.view.KeyEvent
import android.view.View
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import dev.basri.android.nobs_launcher.data.AppCandidate
import dev.basri.android.nobs_launcher.data.AppCatalog
import dev.basri.android.nobs_launcher.data.LayoutStore
import dev.basri.android.nobs_launcher.model.LauncherConfig
import dev.basri.android.nobs_launcher.model.HomeAppSectionsPolicy
import dev.basri.android.nobs_launcher.ui.HomeActivity
import dev.basri.android.nobs_launcher.ui.SettingsActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class HomeAndSettingsFlowTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun clearState() {
        LayoutStore(context).clear()
    }

    @Test
    fun incompleteFirstRunRoutesHomeToSettingsAndBlocksBack() {
        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withId(R.id.welcome_text)).check(matches(isDisplayed()))
            onView(withId(R.id.wifi_label)).check(matches(isDisplayed()))
            onView(withId(R.id.location_label)).check(matches(isDisplayed()))
            onView(withId(R.id.show_location)).check(matches(isChecked()))
            onView(withId(R.id.show_vpn_status)).check(matches(isChecked()))
            onView(withId(R.id.show_system_stats)).check(matches(isChecked()))
            onView(withId(R.id.available_apps)).check(matches(isDisplayed()))
            onView(withId(R.id.open_android_settings)).check(matches(isDisplayed()))
            onView(withId(R.id.save)).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.save)).check(matches(isDisplayed()))
            onView(withId(R.id.save)).perform(click())
        }
    }

    @Test
    fun completedSetupShowsStableHomeControls() {
        LayoutStore(context).save(
            LauncherConfig(
                firstRunComplete = true,
                wifiLabel = "Kahveci House",
                locationLabel = "London",
                selectedPackages = emptyList(),
                welcomeText = "Welcome, Basri",
            ),
        )

        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withId(R.id.clock)).check(matches(isDisplayed()))
            onView(withId(R.id.date)).check(matches(isDisplayed()))
            onView(withId(R.id.welcome)).check(matches(withText("Welcome, Basri")))
            onView(withId(R.id.wifi)).check(matches(withText("Kahveci House")))
            onView(withId(R.id.location)).check(matches(isDisplayed()))
            onView(withId(R.id.vpn_status)).check(matches(isAssignableFrom(TextView::class.java)))
            onView(withId(R.id.system_stats_panel)).check(matches(isDisplayed()))
            onView(withId(R.id.memory_stats)).check(matches(isDisplayed()))
            onView(withId(R.id.cpu_stats)).check(matches(isDisplayed()))
            onView(withId(R.id.storage_stats)).check(matches(isDisplayed()))
            onView(withId(R.id.network_stats)).check(matches(isDisplayed()))
            onView(withId(R.id.settings)).check(matches(isDisplayed()))
            onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
            onView(withId(R.id.empty_state)).check(matches(withEffectiveVisibility(GONE)))
        }
    }

    @Test
    fun settingsStartsNewAppsHiddenAndPersistsAllFieldsAndSelection() {
        val app = uniqueCatalogApps(1).single()
        LayoutStore(context).save(completedConfig())

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(
                allOf(
                    withId(R.id.manage_app_selected),
                    isDescendantOfA(
                        allOf(
                            withId(R.id.manage_app_row),
                            withContentDescription(app.label),
                        ),
                    ),
                ),
            ).check(matches(not(isChecked())))

            onView(withId(R.id.welcome_text)).perform(replaceText("Welcome, Basri"))
            onView(withId(R.id.wifi_label)).perform(replaceText("Kahveci House"))
            onView(withId(R.id.location_label)).perform(replaceText("London"))
            onView(withId(R.id.show_vpn_status)).perform(click())
            onView(withId(R.id.show_system_stats)).perform(click())
            onView(
                allOf(
                    withId(R.id.manage_app_row),
                    withContentDescription(app.label),
                ),
            ).perform(click())
            onView(withId(R.id.save)).perform(click())
        }

        assertEquals(
            LauncherConfig(
                firstRunComplete = true,
                wifiLabel = "Kahveci House",
                locationLabel = "London",
                selectedPackages = listOf(app.packageName),
                welcomeText = "Welcome, Basri",
                showLocation = true,
                showVpnStatus = false,
                showSystemStats = false,
            ),
            LayoutStore(context).load(),
        )
    }

    @Test
    fun laterSettingsBackDiscardsTheWorkingCopy() {
        val original = completedConfig().copy(
            welcomeText = "Original",
            wifiLabel = "Kahveci House",
            locationLabel = "London",
            showLocation = true,
            showVpnStatus = false,
            showSystemStats = true,
        )
        LayoutStore(context).save(original)

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            onView(withId(R.id.welcome_text)).perform(replaceText("Unsaved"))
            onView(withId(R.id.show_location)).perform(click())
            onView(withId(R.id.show_vpn_status)).perform(click())
            onView(withId(R.id.show_system_stats)).perform(click())
            @Suppress("DEPRECATION")
            scenario.onActivity { it.onBackPressed() }
        }

        assertEquals(original, LayoutStore(context).load())
    }

    @Test
    fun homeHonorsWelcomeAndPanelVisibilitySettings() {
        LayoutStore(context).save(
            completedConfig().copy(
                welcomeText = "Box R",
                wifiLabel = "Kahveci House",
                locationLabel = "London",
                showLocation = false,
                showVpnStatus = false,
                showSystemStats = false,
            ),
        )

        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withId(R.id.welcome)).check(matches(withText("Box R")))
            onView(withId(R.id.wifi)).check(matches(withText("Kahveci House")))
            onView(withId(R.id.location)).check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.vpn_status)).check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.system_stats_panel)).check(matches(withEffectiveVisibility(GONE)))
        }
    }

    @Test
    fun homeUsesTwentyEightySplitAndShowsFavoritesThenAlphabeticalRemainingApps() {
        val (first, second) = uniqueCatalogApps(2)
        val favoritePackages = listOf(second.packageName, first.packageName)
        val sections = HomeAppSectionsPolicy.compose(
            catalogApps = AppCatalog(context).loadApps(),
            favoritePackages = favoritePackages,
        )
        LayoutStore(context).save(
            completedConfig(selectedPackages = favoritePackages),
        )

        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withContentDescription(first.label)).check(matches(isDisplayed()))
            onView(withContentDescription(second.label)).check(matches(isDisplayed()))
            onView(withId(R.id.app_grid)).check { view, _ ->
                val grid = view as RecyclerView
                val infoPanel = view.rootView.findViewById<android.view.View>(R.id.info_panel)
                val appsPanel = view.rootView.findViewById<android.view.View>(R.id.apps_panel)
                assertTrue(kotlin.math.abs(appsPanel.width - infoPanel.width * 4) <= 4)

                assertEquals(
                    second.label,
                    grid.findViewHolderForAdapterPosition(0)?.itemView?.contentDescription?.toString(),
                )
                assertEquals(
                    first.label,
                    grid.findViewHolderForAdapterPosition(1)?.itemView?.contentDescription?.toString(),
                )
                val separator = grid.findViewHolderForAdapterPosition(2)?.itemView
                assertEquals(R.id.app_separator, separator?.id)
                assertFalse(separator?.isFocusable ?: true)
                val layoutManager = grid.layoutManager as androidx.recyclerview.widget.GridLayoutManager
                assertEquals(4, layoutManager.spanSizeLookup.getSpanSize(2))
                assertEquals(
                    sections.remaining.first().label,
                    grid.findViewHolderForAdapterPosition(3)?.itemView?.contentDescription?.toString(),
                )
            }
        }
    }

    @Test
    fun homeOmitsSeparatorWhenThereAreNoFavorites() {
        LayoutStore(context).save(completedConfig(selectedPackages = emptyList()))

        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withId(R.id.app_grid)).check { view, _ ->
                val grid = view as RecyclerView
                assertEquals(AppCatalog(context).loadApps().size, grid.adapter?.itemCount)
                assertTrue(
                    (0 until grid.childCount).none { index ->
                        grid.getChildAt(index).id == R.id.app_separator
                    },
                )
            }
        }
    }

    @Test
    fun remainingAppMenuAddsFavoriteAndFavoriteMenuRemovesIt() {
        val app = uniqueCatalogApps(1).single()
        LayoutStore(context).save(completedConfig())

        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withContentDescription(app.label)).perform(longClick())
            onView(withText("Add to favorites")).check(matches(isDisplayed()))
            onView(withText("Uninstall app")).check(matches(isDisplayed()))
            onView(withText("Move")).check(doesNotExist())
            onView(withText("Remove from favorites")).check(doesNotExist())
            onView(withText("Add to favorites")).perform(click())
            assertEquals(listOf(app.packageName), LayoutStore(context).load().selectedPackages)

            onView(withContentDescription(app.label)).perform(longClick())
            onView(withText("Move")).check(matches(isDisplayed()))
            onView(withText("Remove from favorites")).check(matches(isDisplayed()))
            onView(withText("Uninstall app")).check(matches(isDisplayed()))
            onView(withText("Add to favorites")).check(doesNotExist())
            onView(withText("Remove from favorites")).perform(click())
            assertEquals(emptyList<String>(), LayoutStore(context).load().selectedPackages)
        }
    }

    @Test
    fun moveMenuShiftsAcrossRowsOkPersistsAndBackCancels() {
        val apps = uniqueCatalogApps(5)
        val originalOrder = apps.map(AppCandidate::packageName)
        LayoutStore(context).save(completedConfig(selectedPackages = originalOrder))

        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            lateinit var activityDecor: View
            scenario.onActivity { activityDecor = it.window.decorView }
            onView(
                allOf(
                    withId(R.id.app_tile),
                    withContentDescription(apps[3].label),
                ),
            ).inRoot(withDecorView(`is`(activityDecor))).perform(longClick())
            onView(withText("Move")).perform(click())
            onView(withId(R.id.move_mode_hint)).check(matches(isDisplayed()))
            scenario.pressActivityKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            scenario.pressActivityKey(KeyEvent.KEYCODE_DPAD_CENTER)
            val movedOrder = listOf(apps[0], apps[1], apps[2], apps[4], apps[3])
                .map(AppCandidate::packageName)
            assertEquals(movedOrder, LayoutStore(context).load().selectedPackages)
            scenario.finishGridAnimations()

            onView(
                allOf(
                    withId(R.id.app_tile),
                    withContentDescription(apps[3].label),
                ),
            ).inRoot(withDecorView(`is`(activityDecor))).perform(longClick())
            onView(withText("Move")).perform(click())
            scenario.pressActivityKey(KeyEvent.KEYCODE_DPAD_LEFT)
            scenario.pressActivityKey(KeyEvent.KEYCODE_BACK)
            assertEquals(movedOrder, LayoutStore(context).load().selectedPackages)
            onView(withId(R.id.move_mode_hint)).check(matches(withEffectiveVisibility(GONE)))
            scenario.finishGridAnimations()

            onView(
                allOf(
                    withId(R.id.app_tile),
                    withContentDescription(apps[3].label),
                ),
            ).inRoot(withDecorView(`is`(activityDecor))).perform(longClick())
            onView(withText("Move")).perform(click())
            scenario.pressActivityKey(KeyEvent.KEYCODE_DPAD_UP)
            scenario.pressActivityKey(KeyEvent.KEYCODE_DPAD_DOWN)
            scenario.pressActivityKey(KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals(movedOrder, LayoutStore(context).load().selectedPackages)
        }
    }

    @Test
    fun uninstallActionSendsPackageDeleteIntentWithoutChangingFavoriteState() {
        val app = uniqueCatalogApps(1).single()
        val original = completedConfig(selectedPackages = listOf(app.packageName))
        LayoutStore(context).save(original)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var observedIntent: Intent? = null
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_DELETE) return null
                observedIntent = intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)

        try {
            ActivityScenario.launch(HomeActivity::class.java).use {
                onView(withContentDescription(app.label)).perform(longClick())
                onView(withText("Uninstall app")).perform(click())
                assertEquals(Intent.ACTION_DELETE, observedIntent?.action)
                assertEquals("package:${app.packageName}", observedIntent?.data?.toString())
                assertEquals(original, LayoutStore(context).load())
                assertTrue(context.packageManager.getLaunchIntentForPackage(app.packageName) != null)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun failedLaunchKeepsHomeResumedAndShowsAnError() {
        val brokenApp = uniqueCatalogApps(1).single()
        LayoutStore(context).save(completedConfig(listOf(brokenApp.packageName)))
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            val disableResult = runShell("pm disable-user --user 0 ${brokenApp.packageName}")
            check("disabled-user" in disableResult) { disableResult }
            try {
                onView(withContentDescription(brokenApp.label)).perform(click())
                onView(withText(context.getString(R.string.unable_to_open, brokenApp.label)))
                    .check(matches(isDisplayed()))
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            } finally {
                val enableResult = runShell("pm enable --user 0 ${brokenApp.packageName}")
                check("enabled" in enableResult) { enableResult }
            }
        }
    }

    private fun uniqueCatalogApps(count: Int): List<AppCandidate> {
        val unique = AppCatalog(context).loadApps()
            .groupBy { it.label.lowercase() }
            .values
            .filter { it.size == 1 }
            .map(List<AppCandidate>::single)
        check(unique.size >= count) { "Box must expose at least $count uniquely labelled apps" }
        return unique.take(count)
    }

    private fun completedConfig(selectedPackages: List<String> = emptyList()) = LauncherConfig(
        firstRunComplete = true,
        wifiLabel = "",
        locationLabel = "",
        selectedPackages = selectedPackages,
    )

    private fun ActivityScenario<HomeActivity>.pressActivityKey(keyCode: Int) {
        onActivity { activity ->
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun ActivityScenario<HomeActivity>.finishGridAnimations() {
        onActivity { activity ->
            activity.findViewById<RecyclerView>(R.id.app_grid).itemAnimator?.endAnimations()
        }
    }

    private fun runShell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
            .also { descriptor.close() }
    }
}
