package app.gamenative.provider

import android.content.SharedPreferences

class PrefsSecretPersistence(
    private val prefs: SharedPreferences,
) : SecretPersistence {
    override fun put(ref: String, ciphertext: String) {
        prefs.edit().putString(ref, ciphertext).apply()
    }

    override fun get(ref: String): String? = prefs.getString(ref, null)

    override fun remove(ref: String) {
        prefs.edit().remove(ref).apply()
    }
}
