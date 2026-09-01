package app.gamenative.container

import com.winlator.container.Container
import java.io.File

object ContainerLaunchGate {
    fun expectedMarker(container: Container, imageFsVersion: String): PrefixMarker =
        PrefixMarker.expected(
            wineId = container.wineVersion.orEmpty(),
            wincomponents = container.winComponents.orEmpty(),
            dxWrapper = container.dxWrapper.orEmpty(),
            graphicsDriver = container.graphicsDriver.orEmpty(),
            locale = container.lC_ALL.orEmpty(),
            imageFsVersion = imageFsVersion,
        )

    fun isWarm(containerRoot: File, expected: PrefixMarker): Boolean =
        WarmStartPolicy.isWarm(PrefixMarker.read(containerRoot), expected)

    fun recipe(container: Container, sharedPrefix: Boolean): LaunchRecipe {
        val control = ContainerControlStore.read(container.rootDir, sharedPrefix)
        return LaunchRecipe(
            wine = container.wineVersion.orEmpty(),
            translator = container.emulator.orEmpty().ifBlank { container.box64Version.orEmpty() },
            graphics = container.graphicsDriver.orEmpty(),
            dxWrapper = container.dxWrapper.orEmpty(),
            wincomponents = container.winComponents.orEmpty(),
            locale = container.lC_ALL.orEmpty(),
            startup = container.startupSelection.toString(),
            isolation = control.isolation,
            profile = control.profile,
        )
    }

    fun acquirePlay(
        containerRoot: File,
        ownerId: String,
        nowMs: Long,
    ): PrefixLockOwner? {
        val owner = PrefixLock.tryAcquire(containerRoot, ownerId, SessionIoClass.PLAY, nowMs)
        if (owner != null) SessionIoGovernor.begin(SessionIoClass.PLAY)
        return owner
    }

    fun releasePlay(containerRoot: File, ownerId: String) {
        PrefixLock.release(containerRoot, ownerId)
        SessionIoGovernor.end(SessionIoClass.PLAY)
    }

    fun rememberSuccess(
        containerRoot: File,
        expected: PrefixMarker,
        recipe: LaunchRecipe,
        stages: List<LaunchStageTiming>,
        previous: List<LaunchStageTiming>,
        winebootRan: Boolean,
        sharedPrefix: Boolean,
        ttffMs: Long,
    ) {
        PrefixMarker.write(containerRoot, expected.copy(cleanShutdown = true))
        LastLaunchStore.write(containerRoot, stages)
        val explain = ExplainLastLaunch.delta(
            previous = previous,
            current = stages,
            warmExpected = !winebootRan,
            winebootRan = winebootRan,
        )
        val current = ContainerControlStore.read(containerRoot, sharedPrefix)
        ContainerControlStore.write(
            containerRoot,
            current.copy(
                health = if (current.dirty) ContainerHealth.DIRTY else ContainerHealth.WARM,
                recipeHash = recipe.hash(),
                lastTtffMs = ttffMs,
                lastExplain = explain,
            ),
        )
        DualSlot.snapshot(containerRoot, File(containerRoot, ".wine"))
    }
}
