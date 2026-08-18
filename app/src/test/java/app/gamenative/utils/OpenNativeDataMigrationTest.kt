package app.gamenative.utils

import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.SortOption
import java.util.EnumSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenNativeDataMigrationTest {
    @Test
    fun `remote filters are removed while local filters are preserved`() {
        val original = EnumSet.of(
            AppFilter.GAME,
            AppFilter.INSTALLED,
            AppFilter.COMPATIBLE,
            AppFilter.PLAYABLE,
            AppFilter.FIVE_STAR_GPU,
        )

        val migrated = AppFilter.fromFlags(
            OpenNativeDataMigration.sanitizeFilterFlags(AppFilter.toFlags(original)),
        )

        assertTrue(migrated.contains(AppFilter.GAME))
        assertTrue(migrated.contains(AppFilter.INSTALLED))
        assertFalse(migrated.contains(AppFilter.COMPATIBLE))
        assertFalse(migrated.contains(AppFilter.PLAYABLE))
        assertFalse(migrated.contains(AppFilter.FIVE_STAR_GPU))
    }

    @Test
    fun `migration restores game filter when only remote filters were selected`() {
        val remoteOnly = EnumSet.of(AppFilter.COMPATIBLE, AppFilter.PROVEN_GPU)
        val migrated = AppFilter.fromFlags(
            OpenNativeDataMigration.sanitizeFilterFlags(AppFilter.toFlags(remoteOnly)),
        )

        assertEquals(EnumSet.of(AppFilter.GAME), migrated)
    }

    @Test
    fun `remote sorts reset but local sorts remain unchanged`() {
        assertEquals(
            SortOption.INSTALLED_FIRST,
            OpenNativeDataMigration.sanitizeSort(SortOption.FPS_HIGH),
        )
        assertEquals(
            SortOption.RECENTLY_PLAYED,
            OpenNativeDataMigration.sanitizeSort(SortOption.RECENTLY_PLAYED),
        )
    }
}
