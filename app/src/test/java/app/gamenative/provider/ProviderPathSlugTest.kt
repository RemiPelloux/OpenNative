package app.gamenative.provider

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPathSlugTest {
    @Test
    fun `slugs installer folders for Wine`() {
        assertEquals("darkest-dungeon-fitgirl-repack", ProviderPathSlug.slug("Darkest Dungeon [FitGirl Repack]"))
        assertEquals("twisted-tower-v1-0-3", ProviderPathSlug.slug("Twisted Tower – v1.0.3"))
        assertEquals("game", ProviderPathSlug.slug("???"))
        assertEquals(
            "darkest-dungeon-fitgirl-repack/setup.exe",
            ProviderPathSlug.slugDirectories("Darkest Dungeon [FitGirl Repack]/setup.exe"),
        )
        assertEquals("setup.exe", ProviderPathSlug.slugDirectories("setup.exe"))
    }

    @Test
    fun `renames a legacy title folder to the slug`() {
        val root = kotlin.io.path.createTempDirectory("provider-slug").toFile()
        val title = "Darkest Dungeon The Collectors Edition v27760  7 DLCsBonuses"
        File(root, ProviderPathSlug.legacy(title)).mkdirs()
        val dest = ProviderPathSlug.ensureFolder(title, root)
        assertEquals("darkest-dungeon-the-collectors-edition-v27760-7-dlcsbonuses", dest.name)
        assertTrue(dest.isDirectory)
        root.deleteRecursively()
    }
}
