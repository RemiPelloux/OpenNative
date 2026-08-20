package app.gamenative.provider

import java.util.concurrent.atomic.AtomicBoolean

object ProviderSessionGate {
    private val gameOrInstallActive = AtomicBoolean(false)

    fun setActive(active: Boolean) {
        gameOrInstallActive.set(active)
    }

    fun isActive(): Boolean = gameOrInstallActive.get()

    fun allowCatalogWork(): Boolean = !isActive()
}
