package dev.basri.android.nobs_launcher

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import android.view.KeyEvent
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.View
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import dev.basri.android.nobs_launcher.data.AppCandidate
import dev.basri.android.nobs_launcher.data.AppCatalog
import dev.basri.android.nobs_launcher.data.LayoutStore
import dev.basri.android.nobs_launcher.model.LauncherConfig
import dev.basri.android.nobs_launcher.model.HomeAppSectionsPolicy
import dev.basri.android.nobs_launcher.model.HomeItemId
import dev.basri.android.nobs_launcher.model.WebShortcut
import dev.basri.android.nobs_launcher.ui.HomeActivity
import dev.basri.android.nobs_launcher.ui.SettingsActivity
import dev.basri.android.nobs_launcher.ui.WebShortcutsActivity
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
import android.os.SystemClock

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
                favoriteItemIds = emptyList(),
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

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
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
            scenario.onActivity { settings ->
                val appList = settings.findViewById<RecyclerView>(R.id.available_apps)
                val appRow = (0 until appList.childCount)
                    .map(appList::getChildAt)
                    .single { it.contentDescription?.toString() == app.label }
                appRow.performClick()
                settings.findViewById<View>(R.id.save).performClick()
            }
        }

        assertEquals(
            LauncherConfig(
                firstRunComplete = true,
                wifiLabel = "Kahveci House",
                locationLabel = "London",
                favoriteItemIds = listOf(HomeItemId.app(app.packageName)),
                welcomeText = "Welcome, Basri",
                showLocation = true,
                showVpnStatus = false,
                showSystemStats = false,
            ),
            LayoutStore(context).load(),
        )
    }

    @Test
    fun settingsManagesValidatedEditableWebShortcutsAndConfirmedRemoval() {
        LayoutStore(context).save(completedConfig())

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.web_shortcuts)).check(matches(isDisplayed())).perform(click())
            onView(withId(R.id.shortcut_empty)).check(matches(isDisplayed()))
            onView(withId(R.id.shortcut_list)).check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.add_shortcut)).check(matches(isDisplayed())).perform(click())

            onView(withId(R.id.shortcut_name)).perform(replaceText("Basri's site"))
            onView(withId(R.id.shortcut_url)).perform(replaceText("file:///not-allowed"))
            closeSoftKeyboard()
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.shortcut_url)).check(
                matches(hasErrorText(context.getString(R.string.shortcut_url_invalid))),
            )

            onView(withId(R.id.shortcut_url)).perform(
                replaceText("http://127.0.0.1:9/first"),
            )
            closeSoftKeyboard()
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            onView(withContentDescription("Basri's site")).check(matches(isDisplayed()))
            onView(withText("http://127.0.0.1:9/first")).check(matches(isDisplayed()))

            val created = LayoutStore(context).load().shortcuts.single()
            val favoriteConfig = LayoutStore(context).load().copy(
                favoriteItemIds = listOf(HomeItemId.web(created.uuid)),
            )
            assertTrue(LayoutStore(context).save(favoriteConfig))

            onView(withId(R.id.shortcut_edit)).perform(click())
            onView(withId(R.id.shortcut_name)).perform(replaceText("Edited site"))
            onView(withId(R.id.shortcut_url)).perform(
                replaceText("http://127.0.0.1:9/second"),
            )
            closeSoftKeyboard()
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

            val edited = LayoutStore(context).load().shortcuts.single()
            assertEquals(created.uuid, edited.uuid)
            assertEquals("Edited site", edited.name)
            assertEquals("http://127.0.0.1:9/second", edited.url)
            assertEquals(listOf(HomeItemId.web(created.uuid)), LayoutStore(context).load().favoriteItemIds)

            onView(withId(R.id.shortcut_remove)).perform(click())
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            onView(withContentDescription("Edited site")).check(matches(isDisplayed()))

            onView(withId(R.id.shortcut_remove)).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            val afterRemoval = LayoutStore(context).load()
            assertEquals(emptyList<dev.basri.android.nobs_launcher.model.WebShortcut>(), afterRemoval.shortcuts)
            assertEquals(emptyList<String>(), afterRemoval.favoriteItemIds)
            onView(withId(R.id.shortcut_list)).check { view, _ ->
                assertEquals(0, (view as RecyclerView).adapter?.itemCount)
            }
            onView(withId(R.id.shortcut_list)).check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.shortcut_empty)).check(matches(isDisplayed()))

            onView(withId(R.id.back_to_settings)).perform(click())
            onView(withId(R.id.web_shortcuts)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun settingsSavePreservesShortcutStateChangedAfterItsWorkingCopyWasCaptured() {
        LayoutStore(context).save(completedConfig())
        val shortcut = webShortcut(name = "Persisted web", url = "https://example.com/persisted")

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { settings ->
                val current = LayoutStore(settings).load()
                assertTrue(
                    LayoutStore(settings).save(
                        current.copy(
                            favoriteItemIds = current.favoriteItemIds + shortcut.itemId,
                            shortcuts = listOf(shortcut),
                        ),
                    ),
                )
                settings.findViewById<View>(R.id.save).performClick()
            }
        }

        val saved = LayoutStore(context).load()
        assertEquals(listOf(shortcut), saved.shortcuts)
        assertEquals(listOf(shortcut.itemId), saved.favoriteItemIds)
    }

    @Test
    fun webShortcutManagementSupportsDpadAddRowAndSafeRemovalFocus() {
        LayoutStore(context).save(completedConfig())

        ActivityScenario.launch(WebShortcutsActivity::class.java).use {
            sendDpadKey(KeyEvent.KEYCODE_DPAD_LEFT)
            onView(withId(R.id.add_shortcut)).check(matches(hasFocus()))
        }

        val shortcut = webShortcut(name = "D-pad web", url = "http://127.0.0.1:9/dpad")
        LayoutStore(context).save(completedConfig().copy(shortcuts = listOf(shortcut)))

        ActivityScenario.launch(WebShortcutsActivity::class.java).use {
            onView(withContentDescription("D-pad web")).check(matches(isDisplayed()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_LEFT)
            onView(withId(R.id.shortcut_edit)).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            onView(withId(R.id.shortcut_remove)).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_CENTER)
            onView(withId(android.R.id.button2)).inRoot(isDialog()).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_CENTER)
            onView(withContentDescription("D-pad web")).check(matches(isDisplayed()))
        }
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
    fun legacyPackageFavoritesMigrateAtomicallyInExactOrder() {
        val preferences = context.getSharedPreferences(
            LayoutStore.PREFERENCES_NAME,
            android.content.Context.MODE_PRIVATE,
        )
        val legacyPackages = listOf("com.example.second", "com.example.first")
        assertTrue(
            preferences.edit()
                .putString("selected_packages", legacyPackages.joinToString("\n"))
                .commit(),
        )

        val loaded = LayoutStore(context).load()

        assertEquals(legacyPackages.map(HomeItemId::app), loaded.favoriteItemIds)
        assertTrue(preferences.contains("favorite_item_ids"))
        assertFalse(preferences.contains("selected_packages"))
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
    fun homeAlignsHeaderTopAndSettingsWithRightmostAppColumn() {
        val apps = uniqueCatalogApps(4)
        LayoutStore(context).save(
            completedConfig(selectedPackages = apps.map(AppCandidate::packageName)).copy(
                welcomeText = "Welcome, Basri",
            ),
        )

        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withId(R.id.app_grid)).check { view, _ ->
                val grid = view as RecyclerView
                val root = view.rootView
                val welcome = root.findViewById<View>(R.id.welcome).screenBounds()
                val appsTitle = root.findViewById<View>(R.id.apps_title).screenBounds()
                val settings = root.findViewById<View>(R.id.settings).screenBounds()
                val rightmostTile = checkNotNull(
                    grid.findViewHolderForAdapterPosition(3)?.itemView,
                ).screenBounds()

                assertEquals(welcome.top, appsTitle.top)
                assertEquals(welcome.top, settings.top)
                assertEquals(rightmostTile.right, settings.right)
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
    fun homeShowsMixedFavoritesAndDispatchesExactBrowserUrl() {
        val app = uniqueCatalogApps(1).single()
        val shortcut = webShortcut(name = "Basri web portal", url = "https://example.com/tv?mode=1")
        LayoutStore(context).save(
            completedConfig().copy(
                favoriteItemIds = listOf(shortcut.itemId, HomeItemId.app(app.packageName)),
                shortcuts = listOf(shortcut),
            ),
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var observedIntent: Intent? = null
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                observedIntent = intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)

        try {
            ActivityScenario.launch(HomeActivity::class.java).use {
                onView(withId(R.id.app_grid)).check { view, _ ->
                    val grid = view as RecyclerView
                    assertEquals(
                        shortcut.name,
                        grid.findViewHolderForAdapterPosition(0)
                            ?.itemView
                            ?.contentDescription
                            ?.toString(),
                    )
                    assertEquals(
                        app.label,
                        grid.findViewHolderForAdapterPosition(1)
                            ?.itemView
                            ?.contentDescription
                            ?.toString(),
                    )
                }
                onView(withContentDescription(shortcut.name)).perform(click())
                assertEquals(Intent.ACTION_VIEW, observedIntent?.action)
                assertEquals(shortcut.url, observedIntent?.data?.toString())
                assertEquals(null, observedIntent?.component)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun shortcutMenusManageFavoritesAndOpenTheExistingEditor() {
        val shortcut = webShortcut(name = "Editable web")
        LayoutStore(context).save(completedConfig().copy(shortcuts = listOf(shortcut)))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var observedEditIntent: Intent? = null
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.component?.className != WebShortcutsActivity::class.java.name) return null
                observedEditIntent = intent
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)

        try {
            ActivityScenario.launch(HomeActivity::class.java).use {
                onView(withContentDescription(shortcut.name)).perform(longClick())
                onView(withText(R.string.add_to_favorites)).check(matches(isDisplayed()))
                onView(withText(R.string.edit_shortcut)).check(matches(isDisplayed()))
                onView(withText(R.string.remove_shortcut)).check(matches(isDisplayed()))
                onView(withText(R.string.uninstall_app)).check(doesNotExist())
                onView(withText(R.string.add_to_favorites)).perform(click())
                assertEquals(listOf(shortcut.itemId), LayoutStore(context).load().favoriteItemIds)

                onView(withContentDescription(shortcut.name)).perform(longClick())
                onView(withText(R.string.move)).check(matches(isDisplayed()))
                onView(withText(R.string.remove_from_favorites)).check(matches(isDisplayed()))
                onView(withText(R.string.edit_shortcut)).perform(click())
                assertEquals(shortcut.uuid, observedEditIntent?.getStringExtra(WebShortcutsActivity.EXTRA_EDIT_SHORTCUT_ID))

                onView(withContentDescription(shortcut.name)).perform(longClick())
                onView(withText(R.string.remove_from_favorites)).perform(click())
                assertEquals(emptyList<String>(), LayoutStore(context).load().favoriteItemIds)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test
    fun mixedMovePersistsWebAndAppIdsAndConfirmedRemovalCleansIcon() {
        val apps = uniqueCatalogApps(2)
        val shortcut = webShortcut(name = "Movable web", icon = "test-old.png")
        val originalOrder = listOf(
            HomeItemId.app(apps[0].packageName),
            shortcut.itemId,
            HomeItemId.app(apps[1].packageName),
        )
        val iconDirectory = java.io.File(
            context.filesDir,
            dev.basri.android.nobs_launcher.data.FaviconRepository.DIRECTORY_NAME,
        ).apply { mkdirs() }
        val iconFile = java.io.File(iconDirectory, checkNotNull(shortcut.faviconFileName))
        assertTrue(iconFile.createNewFile() || iconFile.isFile)
        LayoutStore(context).save(
            completedConfig().copy(
                favoriteItemIds = originalOrder,
                shortcuts = listOf(shortcut),
            ),
        )

        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            onView(withContentDescription(shortcut.name)).perform(longClick())
            onView(withText(R.string.move)).perform(click())
            scenario.pressActivityKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            scenario.pressActivityKey(KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals(
                listOf(originalOrder[0], originalOrder[2], originalOrder[1]),
                LayoutStore(context).load().favoriteItemIds,
            )
            scenario.finishGridAnimations()

            onView(withContentDescription(shortcut.name)).perform(longClick())
            onView(withText(R.string.remove_shortcut)).perform(click())
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            assertTrue(iconFile.isFile)
            assertEquals(listOf(shortcut), LayoutStore(context).load().shortcuts)

            onView(withContentDescription(shortcut.name)).perform(longClick())
            onView(withText(R.string.remove_shortcut)).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            assertFalse(iconFile.exists())
            assertEquals(emptyList<WebShortcut>(), LayoutStore(context).load().shortcuts)
            assertFalse(shortcut.itemId in LayoutStore(context).load().favoriteItemIds)
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
    fun launcherCanRequestPackageDeletion() {
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(
                Manifest.permission.REQUEST_DELETE_PACKAGES,
                context.packageName,
            ),
        )
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
        favoriteItemIds = selectedPackages.map(HomeItemId::app),
    )

    private fun webShortcut(
        name: String,
        url: String = "https://example.com",
        icon: String? = null,
    ) = WebShortcut(
        uuid = "44444444-4444-4444-8444-444444444444",
        name = name,
        url = url,
        faviconFileName = icon,
    )

    private fun <A : Activity> ActivityScenario<A>.pressActivityKey(keyCode: Int) {
        onActivity { activity ->
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun sendDpadKey(keyCode: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val downTime = SystemClock.uptimeMillis()
        val down = KeyEvent(
            downTime,
            downTime,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            0,
            KeyCharacterMap.VIRTUAL_KEYBOARD,
            0,
            0,
            InputDevice.SOURCE_DPAD,
        )
        val up = KeyEvent.changeAction(down, KeyEvent.ACTION_UP)
        assertTrue(instrumentation.uiAutomation.injectInputEvent(down, true))
        assertTrue(instrumentation.uiAutomation.injectInputEvent(up, true))
        instrumentation.waitForIdleSync()
    }

    private fun ActivityScenario<HomeActivity>.finishGridAnimations() {
        onActivity { activity ->
            activity.findViewById<RecyclerView>(R.id.app_grid).itemAnimator?.endAnimations()
        }
    }

    private fun View.screenBounds() = Rect().also { bounds ->
        assertTrue(getGlobalVisibleRect(bounds))
    }

    private fun runShell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
            .also { descriptor.close() }
    }
}
