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
    fun `normalizes credentials before encrypting and reading`() {
        val store = testStore(MemorySecretPersistence())

        val ref = store.saveNamed("global", "  test-key\n")

        assertEquals("global", ref)
        assertEquals("test-key", store.read(ref))
    }

    @Test
    fun `reads a named credential after the store is recreated`() {
        val persistence = MemorySecretPersistence()
        testStore(persistence).saveNamed(ProviderTabBundle.GLOBAL_CREDENTIAL_REF, "test-key")

        val recreated = testStore(persistence)

        assertEquals("test-key", recreated.read(ProviderTabBundle.GLOBAL_CREDENTIAL_REF))
    }

    @Test
    fun `treats corrupted encrypted credentials as unavailable`() {
        val persistence = MemorySecretPersistence().apply { put("broken", "not-base64") }
        val store = testStore(persistence)

        assertEquals(null, store.read("broken"))
    }

    @Test
    fun `global credential replaces an older per-tab credential`() {
        val store = testStore(MemorySecretPersistence())
        store.saveNamed(ProviderTabBundle.GLOBAL_CREDENTIAL_REF, "global-key")
        store.saveNamed("old-tab-ref", "old-key")
        val tab = ProviderTab(
            id = "tab",
            name = "Provider",
            position = 0,
            feedUrl = "https://example.com/feed",
            credentialRef = "old-tab-ref",
        )

        val attached = ProviderCredentials.attachAvailable(tab, store)

        assertEquals(ProviderTabBundle.GLOBAL_CREDENTIAL_REF, attached.credentialRef)
        assertEquals("global-key", ProviderCredentials.requireFor(attached, store))
    }

    @Test
    fun `in flight guard rejects a second refresh`() {
        val guard = InFlightGuard()
        assertTrue(guard.tryAcquire("tab-1"))
        assertTrue(!guard.tryAcquire("tab-1"))
        guard.release("tab-1")
        assertTrue(guard.tryAcquire("tab-1"))
    }

    private fun testStore(persistence: SecretPersistence) = ProviderSecretStore(
        cipher = AesSecretCipher(
            encryptor = SecretEncryptor { bytes -> bytes.reversedArray() },
            decryptor = SecretDecryptor { bytes -> bytes.reversedArray() },
        ),
        persistence = persistence,
    )
}
