package app.gamenative.ui.util

import com.winlator.container.ContainerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PortableContainerProfileTest {
    @Test
    fun `export strips private paths and round trips gameplay fields`() {
        val original = ContainerData(
            screenSize = "1280x720",
            envVars = "WINEESYNC=1 EVSHIM_BASE_PATH=/data/user/0/private/files DXVK_ASYNC=1",
            rendererPresentMode = "fifo",
            sfCompatMode = true,
            sdlControllerAPI = true,
            useSteamInput = false,
            enableXInput = true,
            enableDInput = true,
            disableMouseInput = true,
            externalDisplayMode = "hybrid",
            executablePath = "PokemonEmerald.exe",
        )

        val json = PortableContainerProfile.export(original, true, 30)
        val restored = PortableContainerProfile.apply(ContainerData(), json)

        assertTrue(PortableContainerProfile.isPortable(json))
        assertFalse(json.toString().contains("/data/user"))
        assertFalse(restored.envVars.contains("EVSHIM_BASE_PATH"))
        assertEquals("1280x720", restored.screenSize)
        assertEquals("PokemonEmerald.exe", restored.executablePath)
        assertTrue(restored.sdlControllerAPI)
        assertTrue(restored.enableXInput)
        assertTrue(restored.enableDInput)
        assertTrue(restored.disableMouseInput)
        assertEquals("hybrid", restored.externalDisplayMode)
        assertEquals(30, json.getInt("fpsLimiterTarget"))
    }

    @Test
    fun `absolute executable path is not exported`() {
        val json = PortableContainerProfile.export(
            ContainerData(executablePath = "/storage/emulated/0/private/game.exe"),
            fpsLimiterEnabled = false,
            fpsLimiterTarget = 60,
        )

        assertEquals("", json.getString("executablePath"))
    }
}
