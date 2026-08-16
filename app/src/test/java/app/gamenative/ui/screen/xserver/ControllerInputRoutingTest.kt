package app.gamenative.ui.screen.xserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerInputRoutingTest {
    @Test
    fun playerOneUsesAssignedControllerRoute() {
        assertTrue(isAssignedPlayerSlot(0))
    }

    @Test
    fun allSupportedPlayersUseAssignedControllerRoute() {
        assertTrue(isAssignedPlayerSlot(1))
        assertTrue(isAssignedPlayerSlot(2))
        assertTrue(isAssignedPlayerSlot(3))
    }

    @Test
    fun unassignedAndOutOfRangeSlotsUseFallbackRoute() {
        assertFalse(isAssignedPlayerSlot(-1))
        assertFalse(isAssignedPlayerSlot(4))
    }
}
