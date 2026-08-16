package com.winlator.inputcontrols

import org.junit.Assert.assertEquals
import org.junit.Test

class GamepadStateTest {
    @Test
    fun `sdl hat encodes cardinal directions`() {
        val state = GamepadState()

        state.dpad[0] = true
        assertEquals(0x01.toByte(), state.sdlHat)

        state.dpad[0] = false
        state.dpad[1] = true
        assertEquals(0x02.toByte(), state.sdlHat)

        state.dpad[1] = false
        state.dpad[2] = true
        assertEquals(0x04.toByte(), state.sdlHat)

        state.dpad[2] = false
        state.dpad[3] = true
        assertEquals(0x08.toByte(), state.sdlHat)
    }

    @Test
    fun `sdl hat preserves diagonals and neutral`() {
        val state = GamepadState()
        assertEquals(0.toByte(), state.sdlHat)

        state.dpad[0] = true
        state.dpad[1] = true
        assertEquals(0x03.toByte(), state.sdlHat)
    }
}
