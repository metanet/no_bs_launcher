package dev.basri.android.nobs_launcher.model

import dev.basri.android.nobs_launcher.data.AppCandidate
import java.util.Locale

data class HomeItemSections(
    val favorites: List<HomeItem>,
    val remaining: List<HomeItem>,
)

object HomeItemSectionsPolicy {
    fun compose(
        items: List<HomeItem>,
        favoriteItemIds: List<String>,
    ): HomeItemSections {
        val itemsById = linkedMapOf<String, HomeItem>()
        items.forEach { item ->
            if (item.id !in itemsById) itemsById[item.id] = item
        }
        val favorites = favoriteItemIds
            .distinct()
            .mapNotNull(itemsById::get)
        val favoriteIds = favorites.mapTo(mutableSetOf(), HomeItem::id)
        val remaining = itemsById.values
            .filterNot { it.id in favoriteIds }
            .sortedWith(
                compareBy<HomeItem> { it.label.lowercase(Locale.getDefault()) }
                    .thenBy(::typeRank)
                    .thenBy(HomeItem::id),
            )
        return HomeItemSections(favorites, remaining)
    }

    private fun typeRank(item: HomeItem): Int = when (item) {
        is HomeItem.App -> 0
        is HomeItem.Web -> 1
    }
}

data class HomeAppSections(
    val favorites: List<AppCandidate>,
    val remaining: List<AppCandidate>,
)

object HomeAppSectionsPolicy {
    fun compose(
        catalogApps: List<AppCandidate>,
        favoritePackages: List<String>,
    ): HomeAppSections {
        val sections = HomeItemSectionsPolicy.compose(
            items = catalogApps.map(HomeItem::App),
            favoriteItemIds = favoritePackages.map(HomeItemId::app),
        )
        return HomeAppSections(
            favorites = sections.favorites.mapNotNull { (it as? HomeItem.App)?.candidate },
            remaining = sections.remaining.mapNotNull { (it as? HomeItem.App)?.candidate },
        )
    }
}
