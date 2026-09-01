package app.gamenative.compat

import com.winlator.container.ContainerData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LaunchDoctorTest {
    @Test
    fun `maps fixture failures to a stable reason and recovery`() {
        val missing = LaunchDoctor.classify(LaunchDoctorInput(hint = "missing dxvk component", wine = "proton"))
        assertEquals(LaunchFailureKind.MISSING_COMPONENT, missing.kind)
        assertEquals(LaunchRecovery.REPAIR_COMPONENT, missing.recovery)

        val exe = LaunchDoctor.classify(LaunchDoctorInput(executableMissing = true))
        assertEquals(LaunchFailureKind.INVALID_EXECUTABLE, exe.kind)
        assertEquals(LaunchRecovery.CHOOSE_EXECUTABLE, exe.recovery)

        val graphics = LaunchDoctor.classify(LaunchDoctorInput(hint = "vkCreateDevice failed"))
        assertEquals(LaunchFailureKind.GRAPHICS_INIT, graphics.kind)
        assertEquals(LaunchRecovery.SAFE_LAUNCH, graphics.recovery)

        val locked = LaunchDoctor.classify(LaunchDoctorInput(hint = "prefix lock held by game-b"))
        assertEquals(LaunchFailureKind.PREFIX_LOCKED, locked.kind)
        val volume = LaunchDoctor.classify(LaunchDoctorInput(hint = "volume missing at granted location"))
        assertEquals(LaunchFailureKind.VOLUME_MISSING, volume.kind)
    }

    @Test
    fun `summary includes the active stack and timeline`() {
        val timeline = LaunchTimeline()
        timeline.mark("container")
        timeline.mark("wine")
        timeline.mark("guest")
        val diagnosis = LaunchDoctor.classify(
            LaunchDoctorInput(
                hint = "wineserver crashed",
                wine = "proton-9",
                translator = "box64",
                graphics = "turnip",
                dxWrapper = "dxvk",
                timeline = timeline.stages(),
            ),
        )
        val text = LaunchDoctor.summary(diagnosis)
        assertTrue(text.contains("proton-9"))
        assertTrue(text.contains("box64"))
        assertTrue(text.contains("container → wine → guest"))
        assertFalse(text.contains("/data/user"))
    }

    @Test
    fun `safe launch overlay is reversible and does not mutate the original`() {
        val original = ContainerData(lsfgEnabled = true, envVars = "WINEESYNC=1 DXVK_HUD=1", rendererPresentMode = "mailbox")
        val safe = SafeLaunchPreset.overlay(original)
        assertTrue(original.lsfgEnabled)
        assertFalse(safe.lsfgEnabled)
        assertEquals("DXVK_HUD=1", safe.envVars)
        assertEquals("fifo", safe.rendererPresentMode)
        assertTrue(SafeLaunchPreset.changes(original, safe).contains("frame generation"))
    }

    @Test
    fun `session marker survives until a clean exit`() {
        val dir = createTempDir(prefix = "session-recovery")
        SessionRecovery.markStarted(dir, "game-1", "game", nowMs = 10L)
        assertEquals("game-1", SessionRecovery.pending(dir)?.appId)
        SessionRecovery.markClean(dir)
        assertEquals(null, SessionRecovery.pending(dir))
        SafeLaunchOnce.arm("game-1")
        assertTrue(SafeLaunchOnce.consume("game-1"))
        assertFalse(SafeLaunchOnce.consume("game-1"))
    }
}
