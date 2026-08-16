package com.winlator.winhandler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadApiPolicyTest {
    @Test
    fun xInputModeOnlyExposesXInput() {
        assertTrue(GamepadApiPolicy.isEnabled(WinHandler.PreferredInputApi.XINPUT, true))
        assertFalse(GamepadApiPolicy.isEnabled(WinHandler.PreferredInputApi.XINPUT, false))
    }

    @Test
    fun directInputModeOnlyExposesDirectInput() {
        assertFalse(GamepadApiPolicy.isEnabled(WinHandler.PreferredInputApi.DINPUT, true))
        assertTrue(GamepadApiPolicy.isEnabled(WinHandler.PreferredInputApi.DINPUT, false))
    }

    @Test
    fun automaticAndBothModesExposeEitherApi() {
        assertTrue(GamepadApiPolicy.isEnabled(WinHandler.PreferredInputApi.AUTO, true))
        assertTrue(GamepadApiPolicy.isEnabled(WinHandler.PreferredInputApi.AUTO, false))
        assertTrue(GamepadApiPolicy.isEnabled(WinHandler.PreferredInputApi.BOTH, true))
        assertTrue(GamepadApiPolicy.isEnabled(WinHandler.PreferredInputApi.BOTH, false))
    }
}
