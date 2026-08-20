package app.gamenative.provider

fun interface SecretEncryptor {
    fun encrypt(plain: ByteArray): ByteArray
}

fun interface SecretDecryptor {
    fun decrypt(cipherText: ByteArray): ByteArray
}

class AesSecretCipher(
    private val encryptor: SecretEncryptor,
    private val decryptor: SecretDecryptor,
) {
    fun encrypt(plain: ByteArray): ByteArray = encryptor.encrypt(plain)
    fun decrypt(cipherText: ByteArray): ByteArray = decryptor.decrypt(cipherText)
}
