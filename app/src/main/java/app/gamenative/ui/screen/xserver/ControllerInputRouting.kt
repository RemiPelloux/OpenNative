package app.gamenative.ui.screen.xserver

import com.winlator.winhandler.WinHandler

internal fun isAssignedPlayerSlot(slot: Int): Boolean = slot in 0 until WinHandler.MAX_PLAYERS
