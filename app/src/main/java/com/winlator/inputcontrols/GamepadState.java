package com.winlator.inputcontrols;

import java.nio.ByteBuffer;

public class GamepadState {
    public float thumbLX = 0;
    public float thumbLY = 0;
    public float thumbRX = 0;
    public float thumbRY = 0;
    public float triggerL = 0;
    public float triggerR = 0;
    public final boolean[] dpad = new boolean[4];
    public short buttons = 0;

    public byte getPovHat() {
        byte povHat = -1;
        if (dpad[0] && dpad[1]) povHat = 1;
        else if (dpad[1] && dpad[2]) povHat = 3;
        else if (dpad[2] && dpad[3]) povHat = 5;
        else if (dpad[3] && dpad[0]) povHat = 7;
        else if (dpad[0]) povHat = 0;
        else if (dpad[1]) povHat = 2;
        else if (dpad[2]) povHat = 4;
        else if (dpad[3]) povHat = 6;
        return povHat;
    }

    /**
     * Returns the SDL hat bit mask for the current D-pad state.
     *
     * The shared-memory bridge exposes both the individual SDL buttons and a
     * POV hat.  Keeping the two representations in sync is important for
     * games that read DirectInput's POV instead of the SDL button mapping.
     */
    public byte getSdlHat() {
        byte hat = 0;
        if (dpad[0]) hat |= 0x01; // SDL_HAT_UP
        if (dpad[1]) hat |= 0x02; // SDL_HAT_RIGHT
        if (dpad[2]) hat |= 0x04; // SDL_HAT_DOWN
        if (dpad[3]) hat |= 0x08; // SDL_HAT_LEFT
        return hat;
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.putShort(buttons);
        buffer.put(getPovHat());
        buffer.putShort((short)(thumbLX * Short.MAX_VALUE));
        buffer.putShort((short)(thumbLY * Short.MAX_VALUE));
        buffer.putShort((short)(thumbRX * Short.MAX_VALUE));
        buffer.putShort((short)(thumbRY * Short.MAX_VALUE));
        buffer.put((byte)(triggerL * 255));
        buffer.put((byte)(triggerR * 255));
    }

    public void setPressed(int buttonIdx, boolean pressed) {
        int flag = 1<<buttonIdx;
        if (pressed) {
            buttons |= flag;
        }
        else buttons &= ~flag;
    }

    public boolean isPressed(int buttonIdx) {
        return (buttons & (1<<buttonIdx)) != 0;
    }

    public byte getDPadX() {
        return (byte)(dpad[1] ? 1 : (dpad[3] ? -1 : 0));
    }

    public byte getDPadY() {
        return (byte)(dpad[0] ? -1 : (dpad[2] ? 1 : 0));
    }

    public void copy(GamepadState other) {
        this.thumbLX = other.thumbLX;
        this.thumbLY = other.thumbLY;
        this.thumbRX = other.thumbRX;
        this.thumbRY = other.thumbRY;
        this.triggerL = other.triggerL;
        this.triggerR = other.triggerR;
        this.buttons = other.buttons;
        System.arraycopy(other.dpad, 0, this.dpad, 0, 4);
    }
}
