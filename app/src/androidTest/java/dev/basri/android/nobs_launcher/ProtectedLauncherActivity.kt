package dev.basri.android.nobs_launcher

import android.app.Activity

/**
 * Test-owned launcher target that the production app cannot open because its
 * manifest requires a privileged permission. It exercises launch failures
 * without disabling or otherwise mutating an unrelated installed package.
 */
class ProtectedLauncherActivity : Activity()
