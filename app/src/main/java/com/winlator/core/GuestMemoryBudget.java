package com.winlator.core;

import android.app.ActivityManager;
import android.content.Context;

/**
 * Selects a conservative guest-visible graphics memory budget when a profile
 * still uses the unlimited default. Explicit user budgets always win.
 */
public final class GuestMemoryBudget {
    private static final long MIB = 1024L * 1024L;

    private GuestMemoryBudget() {}

    public static String resolve(Context context, String configuredValue) {
        String configured = configuredValue == null ? "" : configuredValue.trim();
        if (!configured.isEmpty() && !configured.equals("0")) return configured;

        try {
            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) return configured;

            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            return resolveForTotalMemoryMb(memoryInfo.totalMem / MIB, configured);
        } catch (RuntimeException ignored) {
            return configured;
        }
    }

    static String resolveForTotalMemoryMb(long totalMemoryMb, String configuredValue) {
        String configured = configuredValue == null ? "" : configuredValue.trim();
        if (!configured.isEmpty() && !configured.equals("0")) return configured;

        if (totalMemoryMb >= 6L * 1024L && totalMemoryMb < 9L * 1024L) return "3072";
        if (totalMemoryMb >= 9L * 1024L && totalMemoryMb < 14L * 1024L) return "4096";
        return configured;
    }
}
