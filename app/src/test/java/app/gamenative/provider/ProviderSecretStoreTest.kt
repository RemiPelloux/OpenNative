package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSecretStoreTest {
    @Test
    fun `round trips a credential without storing plaintext refs as keys in export`() {
        val cipher = AesSecretCipher(
            encryptor = SecretEncryptor { bytes -> bytes.reversedArray() },
            decryptor = SecretDecryptor { bytes -> bytes.reversedArray() },
        )
        val store = ProviderSecretStore(
            cipher = cipher,
            persistence = MemorySecretPersistence(),
            idFactory = { "ref-1" },
        )
        val ref = store.save("test-key")
        assertEquals("ref-1", ref)
        assertEquals("test-key", store.read(ref))
        assertEquals("configured-on-this-device", store.exportHint(ref))
        assertEquals("none", store.exportHint(null))
    }

    @Test
    fun `in flight guard rejects a second refresh`() {
        val guard = InFlightGuard()
        assertTrue(guard.tryAcquire("tab-1"))
        assertTrue(!guard.tryAcquire("tab-1"))
        guard.release("tab-1")
        assertTrue(guard.tryAcquire("tab-1"))
    }
}
