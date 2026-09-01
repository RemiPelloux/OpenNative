package app.gamenative.container

data class ActivatePlan(
    val skip: Boolean,
    val expectedLink: String,
    val recoverNext: Boolean,
)

object ActivatePolicy {
    const val USER = "xuser"

    fun expectedLink(containerId: String): String = "./$USER-$containerId"

    fun plan(
        containerId: String,
        currentLinkTarget: String?,
        linkExists: Boolean,
        nextExists: Boolean,
    ): ActivatePlan {
        val expected = expectedLink(containerId)
        if (linkExists && isSameTarget(currentLinkTarget, containerId)) {
            return ActivatePlan(skip = true, expectedLink = expected, recoverNext = false)
        }
        return ActivatePlan(
            skip = false,
            expectedLink = expected,
            recoverNext = !linkExists && nextExists,
        )
    }

    fun isSameTarget(currentLinkTarget: String?, containerId: String): Boolean {
        val value = currentLinkTarget?.trim().orEmpty()
        if (value.isEmpty()) return false
        return value == expectedLink(containerId) ||
            value.endsWith("/$USER-$containerId") ||
            value.endsWith("$USER-$containerId")
    }
}
