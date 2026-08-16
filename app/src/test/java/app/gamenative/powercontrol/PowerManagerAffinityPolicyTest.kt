package app.gamenative.powercontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerManagerAffinityPolicyTest {
    private val nativeFallback = setOf(0, 1, 2, 3, 4, 5, 6, 7)
    private val wow64Fallback = setOf(4, 5, 6, 7)

    @Test
    fun noStoredMasksLeaveAffinityAvailableToAnOptInProfile() {
        assertFalse(
            PowerManager.hasExplicitContainerAffinity(
                cpuList = null,
                cpuListWoW64 = null,
                fallbackCores = nativeFallback,
                fallbackWoW64Cores = wow64Fallback,
            ),
        )
    }

    @Test
    fun fallbackMasksAreNotTreatedAsCustomAffinity() {
        assertFalse(
            PowerManager.hasExplicitContainerAffinity(
                cpuList = "0,1,2,3,4,5,6,7",
                cpuListWoW64 = "4-7",
                fallbackCores = nativeFallback,
                fallbackWoW64Cores = wow64Fallback,
            ),
        )
    }

    @Test
    fun customWow64MaskKeepsAffinityOwnedByTheContainer() {
        assertTrue(
            PowerManager.hasExplicitContainerAffinity(
                cpuList = "0-7",
                cpuListWoW64 = "6,7",
                fallbackCores = nativeFallback,
                fallbackWoW64Cores = wow64Fallback,
            ),
        )
    }
}
