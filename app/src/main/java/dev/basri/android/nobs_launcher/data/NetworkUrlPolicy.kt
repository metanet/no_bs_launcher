package dev.basri.android.nobs_launcher.data

import okhttp3.HttpUrl

internal fun isAllowedRedirect(from: HttpUrl, to: HttpUrl): Boolean =
    !(from.isHttps && !to.isHttps) && to.username.isEmpty() && to.password.isEmpty()
