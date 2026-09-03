package dev.basri.android.nobs_launcher.data

import android.content.Context

object LauncherServices {
    @Volatile
    private var faviconRepository: FaviconRepository? = null

    fun favicons(context: Context): FaviconRepository = faviconRepository ?: synchronized(this) {
        faviconRepository ?: FaviconRepository(context.applicationContext).also {
            faviconRepository = it
        }
    }
}
