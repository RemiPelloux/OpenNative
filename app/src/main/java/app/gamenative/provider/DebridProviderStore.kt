package app.gamenative.provider

import android.content.SharedPreferences

class DebridProviderStore(private val preferences: SharedPreferences) {
    var selected: DebridProvider
        get() = DebridProvider.fromStored(preferences.getString(KEY_SELECTED, null))
        set(value) {
            check(preferences.edit().putString(KEY_SELECTED, value.name).commit()) {
                "Could not save debrid provider"
            }
        }

    private companion object {
        const val KEY_SELECTED = "selected_debrid_provider"
    }
}
