package app.gamenative.container

import java.io.File
import org.json.JSONObject

data class ContainerControl(
    val isolation: IsolationTier,
    val profile: LaunchProfile,
    val health: ContainerHealth,
    val recipeHash: String,
    val lastTtffMs: Long,
    val dirty: Boolean,
    val dirtyBy: String,
    val lastExplain: String,
)

object ContainerControlStore {
    const val FILE_NAME = "opennative-control.json"

    fun file(containerRoot: File): File = File(containerRoot, FILE_NAME)

    fun read(containerRoot: File, sharedPrefix: Boolean = false): ContainerControl {
        val file = file(containerRoot)
        if (!file.isFile) {
            return ContainerControl(
                isolation = IsolationTier.from(null, sharedPrefix),
                profile = LaunchProfile.FAST_BOOT,
                health = ContainerHealth.COLD,
                recipeHash = "",
                lastTtffMs = 0L,
                dirty = false,
                dirtyBy = "",
                lastExplain = "",
            )
        }
        val obj = runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
        return ContainerControl(
            isolation = IsolationTier.from(obj.optString("isolation"), sharedPrefix),
            profile = LaunchProfile.from(obj.optString("profile")),
            health = ContainerHealth.from(obj.optString("health")),
            recipeHash = obj.optString("recipeHash"),
            lastTtffMs = obj.optLong("lastTtffMs"),
            dirty = obj.optBoolean("dirty"),
            dirtyBy = obj.optString("dirtyBy"),
            lastExplain = obj.optString("lastExplain"),
        )
    }

    fun write(containerRoot: File, control: ContainerControl) {
        containerRoot.mkdirs()
        file(containerRoot).writeText(
            JSONObject()
                .put("isolation", control.isolation.wireName)
                .put("profile", control.profile.wireName)
                .put("health", control.health.name)
                .put("recipeHash", control.recipeHash)
                .put("lastTtffMs", control.lastTtffMs)
                .put("dirty", control.dirty)
                .put("dirtyBy", control.dirtyBy)
                .put("lastExplain", control.lastExplain)
                .toString(),
        )
    }

    fun markDirty(containerRoot: File, ownerId: String, sharedPrefix: Boolean) {
        val current = read(containerRoot, sharedPrefix)
        write(
            containerRoot,
            current.copy(
                dirty = true,
                health = ContainerHealth.DIRTY,
                dirtyBy = ownerId,
            ),
        )
    }
}
