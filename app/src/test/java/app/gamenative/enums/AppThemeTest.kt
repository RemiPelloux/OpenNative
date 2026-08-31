package app.gamenative.enums

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {
    @Test
    fun storedOrdinalsStayStableForExistingThemes() {
        assertEquals(0, AppTheme.AUTO.ordinal)
        assertEquals(1, AppTheme.DAY.ordinal)
        assertEquals(2, AppTheme.NIGHT.ordinal)
        assertEquals(3, AppTheme.AMOLED.ordinal)
        assertEquals(4, AppTheme.THOR.ordinal)
    }

    @Test
    fun lightAndAmoledFlagsMatchHandheldPresets() {
        assertFalse(AppTheme.DAY.isDark(systemDark = true))
        assertTrue(AppTheme.AUTO.isDark(systemDark = true))
        assertFalse(AppTheme.AUTO.isDark(systemDark = false))
        assertTrue(AppTheme.THOR.isDark(systemDark = false))
        assertTrue(AppTheme.AMOLED.isAmoled)
        assertFalse(AppTheme.OCEAN.isAmoled)
    }

    @Test
    fun themedPresetsUseDistinctSeeds() {
        val seeds = listOf(AppTheme.THOR, AppTheme.OCEAN, AppTheme.FOREST, AppTheme.DUSK, AppTheme.SLATE)
            .map { it.seedColor }
            .toSet()
        assertEquals(5, seeds.size)
    }
}
