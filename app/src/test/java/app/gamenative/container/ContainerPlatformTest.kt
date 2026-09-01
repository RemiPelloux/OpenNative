package app.gamenative.container

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContainerPlatformTest {
    @Test
    fun `warm start skips wineboot when the marker matches and shutdown was clean`() {
        val expected = PrefixMarker.expected("proton", "d3d=1", "dxvk", "turnip", "en_US", "12")
        assertTrue(WarmStartPolicy.isWarm(expected, expected))
        assertFalse(WarmStartPolicy.isWarm(expected.copy(cleanShutdown = false), expected))
        assertTrue(WarmStartPolicy.shouldWineboot(null, expected))
    }

    @Test
    fun `same-id activate is a no-op`() {
        val skip = ActivatePolicy.plan("SHARED_PREFIX", "./xuser-SHARED_PREFIX", true, false)
        assertTrue(skip.skip)
        val switch = ActivatePolicy.plan("other", "./xuser-SHARED_PREFIX", true, false)
        assertFalse(switch.skip)
        assertEquals("./xuser-other", switch.expectedLink)
    }

    @Test
    fun `prefix lock rejects a second owner and releases the first`() {
        val dir = createTempDir(prefix = "prefix-lock")
        val first = PrefixLock.tryAcquire(dir, "game-a", SessionIoClass.PLAY, 10L)
        assertNotNull(first)
        assertNull(PrefixLock.tryAcquire(dir, "game-b", SessionIoClass.INSTALL, 20L))
        PrefixLock.release(dir, "game-a")
        assertNotNull(PrefixLock.tryAcquire(dir, "game-b", SessionIoClass.INSTALL, 30L))
    }

    @Test
    fun `trim never lists saves or steam userdata`() {
        assertTrue(PrefixTrim.isSacred("drive_c/users/xuser/Saved Games/title"))
        assertTrue(PrefixTrim.isSacred("drive_c/Program Files/Steam/userdata/1"))
        val prefix = createTempDir(prefix = "wine-prefix")
        File(prefix, "drive_c/windows/temp").mkdirs()
        File(prefix, "drive_c/windows/temp/a.tmp").writeText("x")
        File(prefix, "drive_c/users/xuser/Saved Games/keep.bin").apply {
            parentFile.mkdirs()
            writeText("save")
        }
        val preview = PrefixTrim.preview(prefix)
        assertTrue(preview.candidates.any { it.kind == "temp" })
        assertTrue(preview.candidates.none { PrefixTrim.isSacred(it.relativePath) })
        PrefixTrim.apply(prefix, preview)
        assertTrue(File(prefix, "drive_c/users/xuser/Saved Games/keep.bin").isFile)
        assertFalse(File(prefix, "drive_c/windows/temp/a.tmp").exists())
    }

    @Test
    fun `play session blocks catalog refresh and trim`() {
        SessionIoGovernor.resetForTests()
        assertTrue(SessionIoGovernor.begin(SessionIoClass.PLAY))
        assertFalse(SessionIoGovernor.allowsCatalogRefresh())
        assertFalse(SessionIoGovernor.allowsTrim())
        SessionIoGovernor.end(SessionIoClass.PLAY)
        assertTrue(SessionIoGovernor.allowsCatalogRefresh())
        SessionIoGovernor.resetForTests()
    }

    @Test
    fun `index reloads only when the config mtime changes`() {
        val first = ContainerIndexBuilder.from(
            listOf(ContainerIndexEntry("a", 10L, ContainerHealth.WARM, "hash", 100L)),
        )
        assertFalse(first.shouldReload("a", 10L))
        assertTrue(first.shouldReload("a", 11L))
        val merged = ContainerIndexBuilder.merge(
            first,
            "a",
            11L,
            ContainerIndexEntry("a", 11L, ContainerHealth.COLD, "hash2", 200L),
        )
        assertEquals(ContainerHealth.COLD, merged.byId("a")?.health)
    }

    @Test
    fun `recipe hash is stable and omits paths`() {
        val recipe = LaunchRecipe(
            wine = "proton-9",
            translator = "box64",
            graphics = "turnip",
            dxWrapper = "dxvk",
            wincomponents = "direct3d=1",
            locale = "en_US",
            startup = "1",
            isolation = IsolationTier.DEDICATED,
            profile = LaunchProfile.FAST_BOOT,
        )
        assertEquals(16, recipe.hash().length)
        assertEquals(recipe.hash(), recipe.hash())
        assertFalse(recipe.hash().contains("/"))
    }

    @Test
    fun `explain last launch names a missed warm start`() {
        val text = ExplainLastLaunch.delta(
            previous = listOf(LaunchStageTiming("wineboot", 0)),
            current = listOf(LaunchStageTiming("wineboot", 800)),
            warmExpected = true,
            winebootRan = true,
        )
        assertTrue(text.contains("wineboot"))
    }

    @Test
    fun `dual slot flip restores the snapshot`() {
        val root = createTempDir(prefix = "slot-root")
        val wine = File(root, ".wine").apply { mkdirs() }
        File(wine, "system.reg").writeText("current")
        PrefixMarker.write(
            root,
            PrefixMarker.expected("wine", "wc", "dxvk", "gpu", "C", "1"),
        )
        assertTrue(DualSlot.snapshot(root, wine))
        File(wine, "system.reg").writeText("broken")
        assertTrue(DualSlot.flip(root, wine))
        assertEquals("current", File(wine, "system.reg").readText())
    }
}
