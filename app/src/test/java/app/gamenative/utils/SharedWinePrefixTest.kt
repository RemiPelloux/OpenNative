package app.gamenative.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedWinePrefixTest {
    @Test
    fun resolve_keepsDedicatedContainer() {
        assertEquals(
            "STEAM_10",
            SharedWinePrefix.resolveContainerId("STEAM_10", hasDedicated = true, sharedEnabled = true),
        )
        assertEquals(
            "CUSTOM_GAME_4",
            SharedWinePrefix.resolveContainerId("CUSTOM_GAME_4", hasDedicated = true, sharedEnabled = false),
        )
    }

    @Test
    fun resolve_usesSharedWhenEnabledAndNoDedicated() {
        assertEquals(
            SharedWinePrefix.CONTAINER_ID,
            SharedWinePrefix.resolveContainerId("STEAM_10", hasDedicated = false, sharedEnabled = true),
        )
    }

    @Test
    fun resolve_createsDedicatedWhenSharedOff() {
        assertEquals(
            "EPIC_8",
            SharedWinePrefix.resolveContainerId("EPIC_8", hasDedicated = false, sharedEnabled = false),
        )
    }

    @Test
    fun sharedId_matchesOnlyCanonicalName() {
        assertTrue(SharedWinePrefix.isSharedId("SHARED_PREFIX"))
        assertFalse(SharedWinePrefix.isSharedId("STEAM_123"))
        assertFalse(SharedWinePrefix.isSharedId("CUSTOM_GAME_1"))
        assertFalse(SharedWinePrefix.isSharedId("SHARED_PREFIX_2"))
    }
}
