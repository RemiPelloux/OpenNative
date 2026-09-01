package app.gamenative.container

data class ContainerIndexEntry(
    val id: String,
    val configMtime: Long,
    val health: ContainerHealth,
    val recipeHash: String,
    val lastTtffMs: Long,
)

data class ContainerIndex(
    val entries: List<ContainerIndexEntry>,
) {
    fun byId(id: String): ContainerIndexEntry? = entries.firstOrNull { it.id == id }

    fun shouldReload(id: String, fileMtime: Long): Boolean {
        val cached = byId(id) ?: return true
        return cached.configMtime != fileMtime
    }
}

object ContainerIndexBuilder {
    fun from(entries: List<ContainerIndexEntry>): ContainerIndex =
        ContainerIndex(entries.sortedBy { it.id })

    fun merge(
        previous: ContainerIndex,
        id: String,
        fileMtime: Long,
        parsed: ContainerIndexEntry?,
    ): ContainerIndex {
        if (parsed == null) {
            return ContainerIndex(previous.entries.filterNot { it.id == id })
        }
        if (!previous.shouldReload(id, fileMtime)) return previous
        val kept = previous.entries.filterNot { it.id == id }
        return from(kept + parsed)
    }
}
