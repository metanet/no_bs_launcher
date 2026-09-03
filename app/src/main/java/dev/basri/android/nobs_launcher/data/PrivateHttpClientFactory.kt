package dev.basri.android.nobs_launcher.data

import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.OkHttpClient

private val PRIVATE_HTTP_BASE_CLIENT: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .rejectUnexpectedPrivatePeers()
        .build()
}

internal fun newPrivateHttpClient(
    perOperationTimeoutMillis: Long,
    overallTimeoutMillis: Long,
): OkHttpClient = PRIVATE_HTTP_BASE_CLIENT.newBuilder()
    .connectTimeout(perOperationTimeoutMillis, TimeUnit.MILLISECONDS)
    .readTimeout(perOperationTimeoutMillis, TimeUnit.MILLISECONDS)
    .callTimeout(overallTimeoutMillis, TimeUnit.MILLISECONDS)
    .build()
