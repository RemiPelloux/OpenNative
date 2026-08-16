package app.gamenative.externaldisplay

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalDisplayInputControllerTest {
    @Test
    fun `cockpit config is parsed case insensitively`() {
        assertEquals(
            ExternalDisplayInputController.Mode.COCKPIT,
            ExternalDisplayInputController.fromConfig("CoCkPiT"),
        )
    }

    @Test
    fun `unknown config remains disabled`() {
        assertEquals(
            ExternalDisplayInputController.Mode.OFF,
            ExternalDisplayInputController.fromConfig("unknown"),
        )
    }
}
