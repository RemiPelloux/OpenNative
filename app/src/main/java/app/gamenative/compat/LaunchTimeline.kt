package app.gamenative.compat

class LaunchTimeline(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val events = ArrayList<String>(8)

    fun mark(stage: String) {
        val name = stage.trim()
        if (name.isEmpty() || events.size >= 8) return
        if (events.lastOrNull() == name) return
        events.add(name)
    }

    fun stages(): List<String> = events.toList()

    fun startedAt(): Long = clock()
}
