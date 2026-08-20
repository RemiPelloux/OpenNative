package app.gamenative.ui.data

import app.gamenative.provider.ProviderTab
import app.gamenative.ui.enums.LibraryTab

sealed class LibrarySelection {
    data class BuiltIn(val tab: LibraryTab) : LibrarySelection()
    data class Provider(val tabId: String) : LibrarySelection()
}

data class ProviderTabChip(
    val id: String,
    val name: String,
    val stale: Boolean = false,
    val hasCredential: Boolean = false,
    val lastFetchedPage: Int = 0,
    val totalPages: Int = 0,
)

fun ProviderTab.toChip(): ProviderTabChip = ProviderTabChip(
    id = id,
    name = name,
    stale = stale,
    hasCredential = hasCredential(),
    lastFetchedPage = lastFetchedPage,
    totalPages = totalPages,
)
