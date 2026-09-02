package dev.basri.android.nobs_launcher.data

import dev.basri.android.nobs_launcher.model.HomeItemId

object HomeItemIdCodec {
    fun encode(itemIds: List<String>): String = itemIds
        .asSequence()
        .filter(HomeItemId::isValid)
        .distinct()
        .joinToString("\n")

    fun decode(value: String): List<String> = value
        .lineSequence()
        .map(String::trim)
        .filter(HomeItemId::isValid)
        .distinct()
        .toList()
}
