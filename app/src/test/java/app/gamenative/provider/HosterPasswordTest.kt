package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class HosterPasswordTest {
    @Test
    fun `reads a labeled file password from html`() {
        val html = """
            <p>Download</p>
            <p><b>Password:</b> skidrowreloaded</p>
            <a href="https://1fichier.com/?abcd1234">1fichier</a>
        """.trimIndent()
        assertEquals("skidrowreloaded", HosterPassword.fromHtml(html))
    }

    @Test
    fun `returns empty when no password is labeled`() {
        assertEquals("", HosterPassword.fromHtml("<a href=\"https://1fichier.com/?x\">file</a>"))
        assertEquals("", HosterPassword.fromHtml(""))
    }
}
