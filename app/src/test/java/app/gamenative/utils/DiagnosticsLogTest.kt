package app.gamenative.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsLogTest {
    @Test
    fun `sanitizer removes credentials and local paths`() {
        val clean = DiagnosticsLog.sanitize(
            "token=abc123 /storage/emulated/0/Download/Game/file.exe " +
                "/data/data/com.example/files/save api_key:secret"
        )

        assertFalse(clean.contains("abc123"))
        assertFalse(clean.contains("Game/file.exe"))
        assertFalse(clean.contains("com.example"))
        assertFalse(clean.contains("api_key:secret"))
        assertTrue(clean.contains("<redacted>"))
        assertTrue(clean.contains("<storage-path>"))
        assertTrue(clean.contains("<app-path>"))
    }
}
