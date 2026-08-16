package com.winlator.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GuestMemoryBudgetTest {
    @Test
    public void explicitBudgetIsNeverOverridden() {
        assertEquals("6144", GuestMemoryBudget.resolveForTotalMemoryMb(12 * 1024L, "6144"));
    }

    @Test
    public void twelveGbDeviceGetsFourGbGuestBudget() {
        assertEquals("4096", GuestMemoryBudget.resolveForTotalMemoryMb(12 * 1024L, "0"));
    }

    @Test
    public void eightGbDeviceGetsThreeGbGuestBudget() {
        assertEquals("3072", GuestMemoryBudget.resolveForTotalMemoryMb(8 * 1024L, ""));
    }

    @Test
    public void highMemoryAndLowMemoryDevicesKeepDefault() {
        assertEquals("0", GuestMemoryBudget.resolveForTotalMemoryMb(16 * 1024L, "0"));
        assertEquals("", GuestMemoryBudget.resolveForTotalMemoryMb(4 * 1024L, ""));
    }
}
