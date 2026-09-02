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
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.rule.GrantPermissionRule
import android.view.KeyEvent
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.Gravity
import android.view.View
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import dev.basri.android.nobs_launcher.data.AppCandidate
import dev.basri.android.nobs_launcher.data.AppCatalog
import dev.basri.android.nobs_launcher.data.FaviconRepository
import dev.basri.android.nobs_launcher.data.LayoutStore
import dev.basri.android.nobs_launcher.model.LauncherConfig
import dev.basri.android.nobs_launcher.model.HomeAppSectionsPolicy
import dev.basri.android.nobs_launcher.model.HomeItemId
import dev.basri.android.nobs_launcher.model.WebShortcut
import dev.basri.android.nobs_launcher.status.SystemLabelReader
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.os.SystemClock

@RunWith(AndroidJUnit4::class)
class HomeAndSettingsFlowTest {
    @get:Rule
    val wifiPermission: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun clearState() {
        LayoutStore(context).clear()
    }

    @Test
    fun incompleteFirstRunRoutesHomeToSettingsAndBlocksBack() {
        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withId(R.id.welcome_text)).check(matches(isDisplayed()))
            onView(withHint("Wi-Fi name")).check(doesNotExist())
            onView(withHint("Location")).check(doesNotExist())
            onView(withId(R.id.show_wifi_name)).check(matches(isChecked()))
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
    fun settingsKeepsQuickActionsInOneTopRowAndOpensBuildInformation() {
        LayoutStore(context).save(completedConfig())

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            onView(withId(R.id.web_shortcuts)).check(matches(isDisplayed()))
            onView(withId(R.id.open_android_settings)).check(matches(isDisplayed()))
            onView(withId(R.id.build_info)).check(matches(isDisplayed()))
            onView(withId(R.id.save)).check(matches(isDisplayed()))
            val buildHash = context.getString(R.string.build_hash, BuildConfig.BUILD_GIT_HASH)
            val buildDate = context.getString(R.string.build_date, BuildConfig.BUILD_DATE_UTC)
            val buildMessage = "$buildHash\n$buildDate"
            onView(withText(buildHash)).check(doesNotExist())
            onView(withId(R.id.build_info)).perform(click())
            onView(withId(android.R.id.message)).inRoot(isDialog())
                .check(matches(withText(buildMessage)))
                .check { view, noViewException ->
                    noViewException?.let { throw it }
                    val message = view as TextView
                    val absoluteGravity = Gravity.getAbsoluteGravity(
                        message.gravity,
                        message.layoutDirection,
                    ) and Gravity.HORIZONTAL_GRAVITY_MASK
                    assertEquals(Gravity.LEFT, absoluteGravity)
                }
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            scenario.onActivity { settings ->
                assertTrue(settings.findViewById<View>(R.id.save).requestFocusFromTouch())
            }
            onView(withId(R.id.save)).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            onView(withId(R.id.web_shortcuts)).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            onView(withId(R.id.open_android_settings)).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            onView(withId(R.id.build_info)).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_LEFT)
            onView(withId(R.id.open_android_settings)).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_LEFT)
            onView(withId(R.id.web_shortcuts)).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_LEFT)
            onView(withId(R.id.save)).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_DOWN)
            onView(withId(R.id.welcome_text)).check(matches(hasFocus()))
            sendDpadKey(KeyEvent.KEYCODE_DPAD_UP)
            onView(withId(R.id.save)).check(matches(hasFocus()))

            scenario.onActivity { settings ->
                val webShortcuts = settings.findViewById<View>(R.id.web_shortcuts)
                val androidSettings = settings.findViewById<View>(R.id.open_android_settings)
                val buildInfo = settings.findViewById<View>(R.id.build_info)
                val save = settings.findViewById<View>(R.id.save)
                val appList = settings.findViewById<View>(R.id.available_apps)
                val topPositions = listOf(
                    webShortcuts.screenBounds().top,
                    androidSettings.screenBounds().top,
                    buildInfo.screenBounds().top,
                    save.screenBounds().top,
                )

                assertEquals(1, topPositions.distinct().size)
                assertTrue(topPositions.first() < appList.screenBounds().top)
                assertTrue(save.screenBounds().left < webShortcuts.screenBounds().left)
                assertTrue(webShortcuts.screenBounds().left < androidSettings.screenBounds().left)
                assertTrue(androidSettings.screenBounds().left < buildInfo.screenBounds().left)
                assertEquals(R.id.web_shortcuts, save.nextFocusRightId)
                assertEquals(R.id.save, webShortcuts.nextFocusLeftId)
                assertEquals(R.id.open_android_settings, webShortcuts.nextFocusRightId)
                assertEquals(R.id.web_shortcuts, androidSettings.nextFocusLeftId)
                assertEquals(R.id.build_info, androidSettings.nextFocusRightId)
                assertEquals(R.id.open_android_settings, buildInfo.nextFocusLeftId)
                listOf(save, webShortcuts, androidSettings, buildInfo).forEach { action ->
                    assertEquals(R.id.welcome_text, action.nextFocusDownId)
                }
                assertEquals(
                    R.id.save,
                    settings.findViewById<View>(R.id.welcome_text).nextFocusUpId,
                )
            }
        }
    }

    @Test
    fun completedSetupShowsStableHomeControls() {
        LayoutStore(context).save(
            LauncherConfig(
                firstRunComplete = true,
                favoriteItemIds = emptyList(),
                welcomeText = "Welcome, Basri",
            ),
        )

        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withId(R.id.clock)).check(matches(isDisplayed()))
            onView(withId(R.id.date)).check(matches(isDisplayed()))
            onView(withId(R.id.welcome)).check(matches(withText("Welcome, Basri")))
            val systemLabels = SystemLabelReader(context)
            val wifiName = systemLabels.wifiName()
            val expectedLocationLabel = systemLabels.locationName()
                ?: context.getString(R.string.location_unavailable)
            if (wifiName == null) {
                onView(withId(R.id.wifi)).check(matches(withEffectiveVisibility(GONE)))
            } else {
                onView(withId(R.id.wifi))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(wifiName)))
            }
            onView(withId(R.id.location)).check(matches(withText(expectedLocationLabel)))
            onView(withId(R.id.vpn_status)).check(matches(isAssignableFrom(TextView::class.java)))
            onView(withId(R.id.system_stats_panel)).check(matches(isDisplayed()))
            onView(withId(R.id.memory_stats)).check(matches(isDisplayed()))
            onView(withId(R.id.cpu_stats)).check(matches(isDisplayed()))
            onView(withId(R.id.storage_stats)).check(matches(isDisplayed()))
            onView(withId(R.id.network_ingress_stats)).check(matches(isDisplayed()))
                .check { view, _ -> assertEquals(1, (view as TextView).lineCount) }
            onView(withId(R.id.network_egress_stats)).check(matches(isDisplayed()))
                .check { view, _ -> assertEquals(1, (view as TextView).lineCount) }
            onView(withId(R.id.settings)).check(matches(isDisplayed()))
            onView(withId(R.id.app_grid)).check(matches(isDisplayed()))
            onView(withId(R.id.empty_state)).check(matches(withEffectiveVisibility(GONE)))
        }
    }

    @Test
    fun settingsStartsNewAppsHiddenAndPersistsDisplayOptionsAndSelection() {
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
            onView(withId(R.id.show_wifi_name)).perform(click())
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
                favoriteItemIds = listOf(HomeItemId.app(app.packageName)),
                welcomeText = "Welcome, Basri",
                showWifiName = false,
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

            lateinit var createdUrl: String
            LoopbackHttpServer().use { server ->
                server.releaseResponse()
                createdUrl = "${server.url}/first"
                onView(withId(R.id.shortcut_url)).perform(replaceText(createdUrl))
                closeSoftKeyboard()
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                waitUntil { LayoutStore(context).load().shortcuts.size == 1 }
            }
            onView(withContentDescription("Basri's site")).check(matches(isDisplayed()))

            val created = LayoutStore(context).load().shortcuts.single()
            assertEquals(createdUrl, created.url)
            val favoriteConfig = LayoutStore(context).load().copy(
                favoriteItemIds = listOf(HomeItemId.web(created.uuid)),
            )
            assertTrue(LayoutStore(context).save(favoriteConfig))

            onView(withId(R.id.shortcut_edit)).perform(click())
            lateinit var editedUrl: String
            LoopbackHttpServer().use { server ->
                server.releaseResponse()
                editedUrl = "${server.url}/second"
                onView(withId(R.id.shortcut_name)).perform(replaceText("Edited site"))
                onView(withId(R.id.shortcut_url)).perform(replaceText(editedUrl))
                closeSoftKeyboard()
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                waitUntil { LayoutStore(context).load().shortcuts.single().name == "Edited site" }
            }

            val edited = LayoutStore(context).load().shortcuts.single()
            assertEquals(created.uuid, edited.uuid)
            assertEquals("Edited site", edited.name)
            assertEquals(editedUrl, edited.url)
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
    fun shortcutEditorShowsCheckingStateThenPersistsReachableWebsite() {
        LayoutStore(context).save(completedConfig())

        LoopbackHttpServer().use { server ->
            ActivityScenario.launch(WebShortcutsActivity::class.java).use {
                onView(withId(R.id.add_shortcut)).perform(click())
                onView(withText(context.getString(R.string.checking_website)))
                    .check(matches(withEffectiveVisibility(GONE)))
                    .check { view, noViewException ->
                        noViewException?.let { throw it }
                        assertEquals(
                            View.ACCESSIBILITY_LIVE_REGION_POLITE,
                            view.accessibilityLiveRegion,
                        )
                    }
                onView(withId(R.id.shortcut_name)).perform(replaceText("Delayed website"))
                onView(withId(R.id.shortcut_url)).perform(replaceText("  ${server.url}  "))
                closeSoftKeyboard()
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

                onView(withText(context.getString(R.string.checking_website)))
                    .check(matches(isDisplayed()))
                onView(withId(R.id.shortcut_name)).check(matches(not(isEnabled())))
                onView(withId(R.id.shortcut_url)).check(matches(not(isEnabled())))
                onView(withId(android.R.id.button1)).inRoot(isDialog())
                    .check(matches(not(isEnabled())))
                onView(withId(android.R.id.button2)).inRoot(isDialog())
                    .check(matches(isEnabled()))
                server.awaitRequest()

                server.releaseResponse()
                waitUntil { LayoutStore(context).load().shortcuts.size == 1 }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()

                onView(withId(R.id.shortcut_name)).check(doesNotExist())
                val saved = LayoutStore(context).load().shortcuts.single()
                assertEquals("Delayed website", saved.name)
                assertEquals(server.url, saved.url)
            }
        }
    }

    @Test
    fun inaccessibleWebsiteKeepsEditorOpenAndDoesNotPersist() {
        LayoutStore(context).save(completedConfig())

        LoopbackHttpServer(responseStatus = "500 Internal Server Error").use { server ->
            server.releaseResponse()
            ActivityScenario.launch(WebShortcutsActivity::class.java).use {
                onView(withId(R.id.add_shortcut)).perform(click())
                onView(withId(R.id.shortcut_name)).perform(replaceText("Unavailable website"))
                onView(withId(R.id.shortcut_url)).perform(replaceText("${server.url}/unreachable"))
                closeSoftKeyboard()
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

                val expectedError = context.getString(R.string.shortcut_website_inaccessible)
                waitForView {
                    onView(withId(R.id.shortcut_url)).check(matches(hasErrorText(expectedError)))
                }
                onView(withId(R.id.shortcut_url)).check(matches(hasFocus()))
                onView(withId(R.id.shortcut_name)).check(matches(isEnabled()))
                onView(withId(R.id.shortcut_url)).check(matches(isEnabled()))
                onView(withId(android.R.id.button1)).inRoot(isDialog()).check(matches(isEnabled()))
                onView(withId(android.R.id.button2)).inRoot(isDialog()).check(matches(isEnabled()))
                assertEquals(emptyList<WebShortcut>(), LayoutStore(context).load().shortcuts)
            }
        }
    }

    @Test
    fun inaccessibleEditPreservesMetadataIconAndFavorite() {
        val original = webShortcut(
            name = "Existing website",
            url = "https://old.example/path",
            icon = "test-inaccessible-edit.png",
        )
        val originalConfig = completedConfig().copy(
            favoriteItemIds = listOf(original.itemId),
            shortcuts = listOf(original),
        )
        val iconDirectory = java.io.File(
            context.filesDir,
            dev.basri.android.nobs_launcher.data.FaviconRepository.DIRECTORY_NAME,
        ).apply { mkdirs() }
        val iconFile = java.io.File(iconDirectory, checkNotNull(original.faviconFileName))
        assertTrue(iconFile.createNewFile() || iconFile.isFile)
        assertTrue(LayoutStore(context).save(originalConfig))

        try {
            LoopbackHttpServer(responseStatus = "500 Internal Server Error").use { server ->
                server.releaseResponse()
                ActivityScenario.launch(WebShortcutsActivity::class.java).use {
                    onView(withId(R.id.shortcut_edit)).perform(click())
                    onView(withId(R.id.shortcut_name)).perform(replaceText("Rejected edit"))
                    onView(withId(R.id.shortcut_url)).perform(replaceText("${server.url}/edit"))
                    closeSoftKeyboard()
                    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

                    val expectedError = context.getString(R.string.shortcut_website_inaccessible)
                    waitForView {
                        onView(withId(R.id.shortcut_url)).check(matches(hasErrorText(expectedError)))
                    }
                    assertEquals(originalConfig, LayoutStore(context).load())
                    assertTrue(iconFile.isFile)
                }
            }
        } finally {
            iconFile.delete()
        }
    }

    @Test
    fun closingEditorDuringWebsiteCheckPreventsLatePersistence() {
        LayoutStore(context).save(completedConfig())

        LoopbackHttpServer().use { server ->
            ActivityScenario.launch(WebShortcutsActivity::class.java).use {
                onView(withId(R.id.add_shortcut)).perform(click())
                onView(withId(R.id.shortcut_name)).perform(replaceText("Cancelled website"))
                onView(withId(R.id.shortcut_url)).perform(replaceText(server.url))
                closeSoftKeyboard()
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                server.awaitRequest()

                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                onView(withId(R.id.shortcut_name)).check(doesNotExist())
                server.releaseResponse()
                server.awaitFinished()

                LoopbackHttpServer().use { barrierServer ->
                    onView(withId(R.id.add_shortcut)).perform(click())
                    onView(withId(R.id.shortcut_name)).perform(replaceText("Barrier website"))
                    onView(withId(R.id.shortcut_url)).perform(replaceText(barrierServer.url))
                    closeSoftKeyboard()
                    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

                    barrierServer.awaitRequest()
                    assertEquals(emptyList<WebShortcut>(), LayoutStore(context).load().shortcuts)

                    onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                    barrierServer.releaseResponse()
                    barrierServer.awaitFinished()
                }
            }
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
            showWifiName = true,
            showLocation = true,
            showVpnStatus = false,
            showSystemStats = true,
        )
        LayoutStore(context).save(original)

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            onView(withId(R.id.welcome_text)).perform(replaceText("Unsaved"))
            onView(withId(R.id.show_wifi_name)).perform(click())
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
    fun savingConfigurationRemovesObsoleteEditableSystemLabels() {
        val preferences = context.getSharedPreferences(
            LayoutStore.PREFERENCES_NAME,
            android.content.Context.MODE_PRIVATE,
        )
        assertTrue(
            preferences.edit()
                .putString("wifi_label", "Manual Wi-Fi")
                .putString("location_label", "Manual location")
                .commit(),
        )

        assertTrue(LayoutStore(context).save(completedConfig()))

        assertFalse(preferences.contains("wifi_label"))
        assertFalse(preferences.contains("location_label"))
    }

    @Test
    fun homeHonorsWelcomeAndPanelVisibilitySettings() {
        LayoutStore(context).save(
            completedConfig().copy(
                welcomeText = "Box R",
                showWifiName = false,
                showLocation = false,
                showVpnStatus = false,
                showSystemStats = false,
            ),
        )

        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withId(R.id.welcome)).check(matches(withText("Box R")))
            onView(withId(R.id.wifi)).check(matches(withEffectiveVisibility(GONE)))
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
    fun downloadedSmallFaviconMatchesLandscapeAppArtworkHeight() {
        val app = landscapeCatalogApp()
        val shortcut = webShortcut(name = "A Tiny Home icon")
        val favicon = storeTinyFavicon(shortcut)
        LayoutStore(context).save(
            completedConfig().copy(
                favoriteItemIds = listOf(shortcut.itemId, HomeItemId.app(app.packageName)),
                shortcuts = listOf(shortcut.copy(faviconFileName = favicon)),
            ),
        )

        try {
            ActivityScenario.launch(HomeActivity::class.java).use {
                var faviconHeight = 0f
                onView(
                    allOf(
                        withId(R.id.app_artwork),
                        isDescendantOfA(withContentDescription(shortcut.name)),
                    ),
                ).check { view, error ->
                    error?.let { throw it }
                    faviconHeight = renderedArtworkHeight(view)
                }
                var appHeight = 0f
                onView(
                    allOf(
                        withId(R.id.app_artwork),
                        isDescendantOfA(withContentDescription(app.label)),
                    ),
                ).check { view, error ->
                    error?.let { throw it }
                    appHeight = renderedArtworkHeight(view)
                }
                assertEquals(
                    "Favicon height must align with ${app.label} artwork",
                    appHeight,
                    faviconHeight,
                    2f,
                )
            }
        } finally {
            FaviconRepository(context).delete(favicon)
        }
    }

    @Test
    fun downloadedSmallFaviconFillsManagementArtworkViewport() {
        val shortcut = webShortcut(name = "Tiny management icon")
        val favicon = storeTinyFavicon(shortcut)
        LayoutStore(context).save(
            completedConfig().copy(
                shortcuts = listOf(shortcut.copy(faviconFileName = favicon)),
            ),
        )

        try {
            ActivityScenario.launch(WebShortcutsActivity::class.java).use {
                onView(
                    allOf(
                        withId(R.id.shortcut_icon),
                        isDescendantOfA(withContentDescription(shortcut.name)),
                    ),
                ).check { view, error ->
                    error?.let { throw it }
                    assertArtworkFillsShorterAxis(view)
                }
            }
        } finally {
            FaviconRepository(context).delete(favicon)
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

    private fun landscapeCatalogApp(): AppCandidate = AppCatalog(context).loadApps()
        .groupBy { it.label.lowercase() }
        .values
        .filter { it.size == 1 }
        .map(List<AppCandidate>::single)
        .first { candidate ->
            val artwork = candidate.artwork ?: return@first false
            val ratio = artwork.intrinsicWidth.toFloat() / artwork.intrinsicHeight.toFloat()
            artwork.intrinsicWidth >= 200 && ratio in 1.7f..1.85f
        }

    private fun completedConfig(selectedPackages: List<String> = emptyList()) = LauncherConfig(
        firstRunComplete = true,
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

    private fun storeTinyFavicon(shortcut: WebShortcut): String {
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        val bytes = ByteArrayOutputStream().use { output ->
            check(source.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        source.recycle()
        return checkNotNull(FaviconRepository(context).store(shortcut.uuid, shortcut.url, bytes))
    }

    private fun assertArtworkFillsShorterAxis(view: View) {
        val image = view as ImageView
        val renderedWidth = renderedArtworkWidth(image)
        val renderedHeight = renderedArtworkHeight(image)
        val requiredExtent = minOf(image.width, image.height) * 0.9f
        assertTrue(
            "Rendered favicon ${renderedWidth}x$renderedHeight did not fill " +
                "the ${image.width}x${image.height} icon viewport",
            maxOf(renderedWidth, renderedHeight) >= requiredExtent,
        )
    }

    private fun renderedArtworkWidth(view: View): Float {
        val image = view as ImageView
        val drawable = checkNotNull(image.drawable)
        val matrixValues = FloatArray(9)
        image.imageMatrix.getValues(matrixValues)
        return drawable.intrinsicWidth * matrixValues[Matrix.MSCALE_X]
    }

    private fun renderedArtworkHeight(view: View): Float {
        val image = view as ImageView
        val drawable = checkNotNull(image.drawable)
        val matrixValues = FloatArray(9)
        image.imageMatrix.getValues(matrixValues)
        return drawable.intrinsicHeight * matrixValues[Matrix.MSCALE_Y]
    }

    private fun waitUntil(
        timeoutMillis: Long = 5_000,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Condition was not met within $timeoutMillis ms" }
            SystemClock.sleep(25)
        }
    }

    private fun waitForView(
        timeoutMillis: Long = 5_000,
        assertion: () -> Unit,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastFailure: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                assertion()
                return
            } catch (error: AssertionError) {
                lastFailure = error
            } catch (error: RuntimeException) {
                lastFailure = error
            }
            SystemClock.sleep(25)
        }
        throw AssertionError("View condition was not met within $timeoutMillis ms", lastFailure)
    }

    private class LoopbackHttpServer(
        private val responseStatus: String = "204 No Content",
    ) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val requestReceived = CountDownLatch(1)
        private val responseReleased = CountDownLatch(1)
        private val finished = CountDownLatch(1)

        @Volatile
        private var failure: Throwable? = null

        val url = "http://127.0.0.1:${server.localPort}"

        init {
            Thread(
                {
                    try {
                        server.accept().use { socket ->
                            socket.soTimeout = 3_000
                            val input = socket.getInputStream()
                            var bytesRead = 0
                            var terminatorBytes = 0
                            while (bytesRead < MAX_REQUEST_BYTES && terminatorBytes < 4) {
                                val next = input.read()
                                if (next == -1) break
                                bytesRead += 1
                                terminatorBytes = when {
                                    terminatorBytes == 0 && next == '\r'.code -> 1
                                    terminatorBytes == 1 && next == '\n'.code -> 2
                                    terminatorBytes == 2 && next == '\r'.code -> 3
                                    terminatorBytes == 3 && next == '\n'.code -> 4
                                    next == '\r'.code -> 1
                                    else -> 0
                                }
                            }
                            check(terminatorBytes == 4) { "Loopback request headers were incomplete" }
                            requestReceived.countDown()
                            check(responseReleased.await(5, TimeUnit.SECONDS)) {
                                "Loopback response was not released"
                            }
                            val response = "HTTP/1.1 $responseStatus\r\nConnection: close\r\n\r\n"
                            socket.getOutputStream().apply {
                                write(response.toByteArray(StandardCharsets.US_ASCII))
                                flush()
                            }
                        }
                    } catch (error: Throwable) {
                        if (!server.isClosed) failure = error
                        requestReceived.countDown()
                    } finally {
                        finished.countDown()
                    }
                },
                "shortcut-test-loopback",
            ).apply {
                isDaemon = true
                start()
            }
        }

        fun awaitRequest() {
            check(requestReceived.await(5, TimeUnit.SECONDS)) { "Website probe never reached loopback" }
            failure?.let { throw AssertionError("Loopback server failed", it) }
        }

        fun releaseResponse() {
            responseReleased.countDown()
        }

        fun awaitFinished() {
            check(finished.await(5, TimeUnit.SECONDS)) { "Loopback server did not finish" }
            failure?.let { throw AssertionError("Loopback server failed", it) }
        }

        override fun close() {
            responseReleased.countDown()
            server.close()
            check(finished.await(5, TimeUnit.SECONDS)) { "Loopback server did not stop" }
            failure?.let { throw AssertionError("Loopback server failed", it) }
        }

        private companion object {
            const val MAX_REQUEST_BYTES = 8 * 1024
        }
    }

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
