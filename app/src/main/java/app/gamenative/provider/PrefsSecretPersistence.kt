package app.gamenative.provider

import android.content.SharedPreferences

class PrefsSecretPersistence(
    private val prefs: SharedPreferences,
) : SecretPersistence {
    override fun put(ref: String, ciphertext: String) {
        check(prefs.edit().putString(ref, ciphertext).commit()) {
            "Could not persist provider credential"
        }
    }

    override fun get(ref: String): String? = prefs.getString(ref, null)

    override fun remove(ref: String) {
        check(prefs.edit().remove(ref).commit()) {
            "Could not remove provider credential"
        }
    }
}
