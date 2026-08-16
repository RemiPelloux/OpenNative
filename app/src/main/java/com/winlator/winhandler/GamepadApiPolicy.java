package com.winlator.winhandler;

/** Selects which guest controller API is exposed for a container. */
public final class GamepadApiPolicy {
    private GamepadApiPolicy() {}

    public static boolean isEnabled(WinHandler.PreferredInputApi preferredApi, boolean isXInput) {
        switch (preferredApi) {
            case DINPUT:
                return !isXInput;
            case XINPUT:
                return isXInput;
            case AUTO:
            case BOTH:
            default:
                return true;
        }
    }
}
