package app.gamenative.container

import java.util.concurrent.atomic.AtomicReference

object SessionIoGovernor {
    private val active = AtomicReference<SessionIoClass?>(null)

    fun begin(cls: SessionIoClass): Boolean {
        val current = active.get()
        if (current != null && current != cls && !canPreempt(current, cls)) return false
        active.set(cls)
        return true
    }

    fun end(cls: SessionIoClass) {
        active.compareAndSet(cls, null)
    }

    fun activeClass(): SessionIoClass? = active.get()

    fun allowsCatalogRefresh(): Boolean = active.get() != SessionIoClass.PLAY

    fun allowsTrim(): Boolean = active.get() == null

    fun allows(cls: SessionIoClass): Boolean {
        val current = active.get() ?: return true
        return current == cls || canPreempt(current, cls)
    }

    fun resetForTests() {
        active.set(null)
    }

    private fun canPreempt(current: SessionIoClass, incoming: SessionIoClass): Boolean =
        current == SessionIoClass.MAINTAIN && incoming != SessionIoClass.MAINTAIN
}
