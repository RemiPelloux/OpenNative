package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderUrlPolicyTest {
    @Test
    fun `https feed is accepted`() {
        val result = ProviderUrlPolicy.validate("https://example.com/feed.json")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `http feed is rejected`() {
        val result = ProviderUrlPolicy.validate("http://example.com/feed.json")
        assertTrue(result.isFailure)
        assertEquals(ProviderErrorCode.UNSAFE_URL, (result.exceptionOrNull() as ProviderException).code)
    }

    @Test
    fun `loopback http is allowed for tests`() {
        val result = ProviderUrlPolicy.validate("http://127.0.0.1:8080/feed", allowLoopbackHttp = true)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `blocked catalog host is rejected`() {
        val result = ProviderUrlPolicy.validate("https://fitgirl-repacks.site/wp-json/wp/v2/posts")
        assertTrue(result.isFailure)
    }

    @Test
    fun `redacts bearer tokens`() {
        val redacted = ProviderUrlPolicy.redact("Authorization: Bearer super-secret")
        assertTrue(redacted.contains("[redacted]"))
        assertTrue(!redacted.contains("super-secret"))
    }
}
