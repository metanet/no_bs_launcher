package dev.basri.android.nobs_launcher.model

import dev.basri.android.nobs_launcher.data.AppCandidate
import java.util.Locale

data class HomeAppSections(
    val favorites: List<AppCandidate>,
    val remaining: List<AppCandidate>,
)

object HomeAppSectionsPolicy {
    fun compose(
        catalogApps: List<AppCandidate>,
        favoritePackages: List<String>,
    ): HomeAppSections {
        val candidatesByPackage = catalogApps.associateBy(AppCandidate::packageName)
        val favorites = favoritePackages
            .distinct()
            .mapNotNull(candidatesByPackage::get)
        val favoriteSet = favorites.mapTo(mutableSetOf(), AppCandidate::packageName)
        val remaining = candidatesByPackage.values
            .filterNot { it.packageName in favoriteSet }
            .sortedWith(
                compareBy<AppCandidate> { it.label.lowercase(Locale.getDefault()) }
                    .thenBy(AppCandidate::packageName),
            )
        return HomeAppSections(
            favorites = favorites,
            remaining = remaining,
        )
    }
}
