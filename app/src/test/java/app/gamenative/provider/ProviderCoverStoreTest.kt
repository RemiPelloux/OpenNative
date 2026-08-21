package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderCoverStoreTest {
    @Test
    fun `uses the artwork file extension when present`() {
        assertEquals("png", ProviderCoverStore.extension("https://cdn.example.com/cover.png?w=400"))
        assertEquals("webp", ProviderCoverStore.extension("https://cdn.example.com/art.webp"))
        assertEquals("jpg", ProviderCoverStore.extension("https://cdn.example.com/image"))
    }
}
