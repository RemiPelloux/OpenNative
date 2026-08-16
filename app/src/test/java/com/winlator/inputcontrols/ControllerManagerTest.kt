package com.winlator.inputcontrols

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ControllerManagerTest {
    private lateinit var manager: ControllerManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        manager = ControllerManager.getInstance()
        manager.init(context)
        disableAllSlots()
    }

    @After
    fun tearDown() {
        disableAllSlots()
        manager.setSlotEnabled(0, true)
    }

    @Test
    fun provisionedPlayerCount_defaultsToOneWhenAllSlotsAreDisabled() {
        assertEquals(1, manager.provisionedPlayerCount)
    }

    @Test
    fun provisionedPlayerCount_includesSparseEnabledSlots() {
        manager.setSlotEnabled(0, true)
        manager.setSlotEnabled(2, true)

        assertEquals(3, manager.provisionedPlayerCount)
    }

    @Test
    fun provisionedPlayerCount_doesNotProvisionUnusedTrailingSlots() {
        manager.setSlotEnabled(0, true)

        assertEquals(1, manager.provisionedPlayerCount)
    }

    private fun disableAllSlots() {
        repeat(4) { manager.setSlotEnabled(it, false) }
    }
}
