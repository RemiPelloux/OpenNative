package app.gamenative.utils

import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.SortOption
import java.util.EnumSet

/** Pure migration rules for data inherited from disabled remote OpenNative services. */
object OpenNativeDataMigration {
    const val VERSION = 1

    private val remoteFilters = setOf(
        AppFilter.COMPATIBLE,
        AppFilter.PLAYABLE,
        AppFilter.FIVE_STAR,
        AppFilter.FIVE_STAR_GPU,
        AppFilter.PROVEN_GPU,
    )

    private val localAppTypes = setOf(
        AppFilter.GAME,
        AppFilter.APPLICATION,
        AppFilter.TOOL,
        AppFilter.DEMO,
    )

    private val remoteSorts = setOf(
        SortOption.FPS_HIGH,
        SortOption.RUNS_HIGH,
        SortOption.REVIEWS_HIGH,
        SortOption.REVIEWS_GPU_HIGH,
    )

    fun sanitizeFilterFlags(flags: Int): Int {
        val filters = AppFilter.fromFlags(flags).apply { removeAll(remoteFilters) }
        if (filters.none { it in localAppTypes }) filters.add(AppFilter.GAME)
        return AppFilter.toFlags(filters)
    }

    fun sanitizeSort(sort: SortOption): SortOption =
        if (sort in remoteSorts) SortOption.INSTALLED_FIRST else sort

    fun sanitizeSortKey(key: String): String =
        sanitizeSort(SortOption.fromKey(key)).key

    fun defaultFilterFlags(): Int =
        AppFilter.toFlags(EnumSet.of(AppFilter.GAME, AppFilter.SHARED))
}
