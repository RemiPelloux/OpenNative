package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {
    @Test
    fun `decodes cuphead wordpress title entities`() {
        val raw = "Cuphead: Game &#038; Soundtrack Bundle &#8211; v1.3.9 + DLC + Bonus OSTs"
        assertEquals(
            "Cuphead: Game & Soundtrack Bundle – v1.3.9 + DLC + Bonus OSTs",
            HtmlText.decode(raw),
        )
    }

    @Test
    fun `decodes named and hex entities after stripping tags`() {
        val html = "<p>RUS/ENG &amp; MULTi12 &#x26; more&nbsp;text</p>"
        assertEquals("RUS/ENG & MULTi12 & more text", HtmlText.plain(html))
    }

    @Test
    fun `decodes double encoded ampersand`() {
        assertEquals("A & B", HtmlText.decode("A &amp;#038; B"))
    }
}
