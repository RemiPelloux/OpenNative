package app.gamenative.provider

import java.util.Base64
import java.util.UUID

/**
 * Stores provider credentials as opaque references. Ciphertext never enters Room.
 */
class ProviderSecretStore(
    private val cipher: AesSecretCipher,
    private val persistence: SecretPersistence,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun save(secret: String): String {
        require(secret.isNotBlank()) { "Credential cannot be blank" }
        return saveNamed(idFactory(), secret)
    }

    fun saveNamed(ref: String, secret: String): String {
        require(secret.isNotBlank()) { "Credential cannot be blank" }
        require(ref.isNotBlank()) { "Credential ref cannot be blank" }
        val encrypted = cipher.encrypt(secret.toByteArray(Charsets.UTF_8))
        persistence.put(ref, Base64.getEncoder().encodeToString(encrypted))
        return ref
    }

    fun read(ref: String?): String? {
        if (ref.isNullOrBlank()) return null
        val stored = persistence.get(ref) ?: return null
        val bytes = Base64.getDecoder().decode(stored)
        return cipher.decrypt(bytes).toString(Charsets.UTF_8)
    }

    fun delete(ref: String?) {
        if (!ref.isNullOrBlank()) persistence.remove(ref)
    }

    fun exportHint(ref: String?): String =
        if (ref.isNullOrBlank()) "none" else "configured-on-this-device"
}

interface SecretPersistence {
    fun put(ref: String, ciphertext: String)
    fun get(ref: String): String?
    fun remove(ref: String)
}

class MemorySecretPersistence : SecretPersistence {
    private val values = linkedMapOf<String, String>()
    override fun put(ref: String, ciphertext: String) {
        values[ref] = ciphertext
    }
    override fun get(ref: String): String? = values[ref]
    override fun remove(ref: String) {
        values.remove(ref)
    }
}
