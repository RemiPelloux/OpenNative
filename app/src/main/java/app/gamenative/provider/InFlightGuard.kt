package app.gamenative.provider

import java.util.concurrent.ConcurrentHashMap

class InFlightGuard {
    private val keys = ConcurrentHashMap.newKeySet<String>()

    fun tryAcquire(key: String): Boolean = keys.add(key)

    fun release(key: String) {
        keys.remove(key)
    }

    suspend fun <T> withKey(key: String, block: suspend () -> T): T? {
        if (!tryAcquire(key)) return null
        return try {
            block()
        } finally {
            release(key)
        }
    }
}
