package app.gamenative.provider

import app.gamenative.ui.data.LibrarySelection
import app.gamenative.ui.data.ProviderTabChip
import app.gamenative.ui.enums.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionTest {
    @Test
    fun `provider tabs sit after built-in tabs`() {
        val builtIn = listOf(LibraryTab.ALL, LibraryTab.LOCAL).map { LibrarySelection.BuiltIn(it) }
        val providers = listOf(ProviderTabChip("p1", "Mine")).map { LibrarySelection.Provider(it.id) }
        val all = builtIn + providers
        assertEquals(LibrarySelection.BuiltIn(LibraryTab.ALL), all.first())
        assertEquals(LibrarySelection.Provider("p1"), all.last())
        assertTrue(all.indexOf(LibrarySelection.BuiltIn(LibraryTab.LOCAL)) < all.indexOf(LibrarySelection.Provider("p1")))
    }
}
