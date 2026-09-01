package app.gamenative.container

import java.io.File
import org.json.JSONObject

data class PrefixLockOwner(
    val ownerId: String,
    val kind: SessionIoClass,
    val acquiredAtMs: Long,
)

object PrefixLock {
    const val FILE_NAME = ".opennative-prefix.lock"
    const val STALE_MS = 4L * 60L * 60L * 1000L

    fun file(containerRoot: File): File = File(containerRoot, FILE_NAME)

    fun peek(containerRoot: File): PrefixLockOwner? {
        val file = file(containerRoot)
        if (!file.isFile) return null
        return runCatching { readOwner(file) }.getOrNull()
    }

    fun tryAcquire(
        containerRoot: File,
        ownerId: String,
        kind: SessionIoClass,
        nowMs: Long,
    ): PrefixLockOwner? {
        val current = peek(containerRoot)
        if (current != null && current.ownerId != ownerId && nowMs - current.acquiredAtMs < STALE_MS) {
            return null
        }
        val owner = PrefixLockOwner(ownerId, kind, nowMs)
        write(containerRoot, owner)
        return owner
    }

    fun release(containerRoot: File, ownerId: String) {
        val current = peek(containerRoot) ?: return
        if (current.ownerId != ownerId) return
        file(containerRoot).delete()
    }

    fun blockedHint(owner: PrefixLockOwner): String =
        "prefix lock held by ${owner.ownerId} (${owner.kind.name.lowercase()})"

    private fun write(containerRoot: File, owner: PrefixLockOwner) {
        containerRoot.mkdirs()
        file(containerRoot).writeText(
            JSONObject()
                .put("ownerId", owner.ownerId)
                .put("kind", owner.kind.name)
                .put("acquiredAtMs", owner.acquiredAtMs)
                .toString(),
        )
    }

    private fun readOwner(file: File): PrefixLockOwner {
        val obj = JSONObject(file.readText())
        return PrefixLockOwner(
            ownerId = obj.getString("ownerId"),
            kind = SessionIoClass.valueOf(obj.getString("kind")),
            acquiredAtMs = obj.optLong("acquiredAtMs"),
        )
    }
}
