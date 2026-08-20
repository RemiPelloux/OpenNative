package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderGameUiTest {
    @Test
    fun `decodes cuphead title for the game sheet`() {
        val item = ProviderFeedItem(
            itemId = "5361",
            title = "Cuphead: Game &#038; Soundtrack Bundle &#8211; v1.3.9",
            link = "https://fitgirl-repacks.site/cuphead/",
        )
        assertEquals("Cuphead: Game & Soundtrack Bundle – v1.3.9", ProviderGameUi.title(item))
    }

    @Test
    fun `hides zero byte download labels`() {
        val empty = ProviderFeedItem(itemId = "1", title = "Game", link = "https://example.com/g")
        assertEquals("", ProviderGameUi.sizeLine(empty))
        val fromExcerpt = empty.copy(description = "Original Size: 6.9 GB Repack Size: 3.6/4 GB")
        assertTrue(ProviderGameUi.sizeLine(fromExcerpt).isNotBlank())
    }

    @Test
    fun `detects a local installer and does not treat ready jobs as downloadable`() {
        val root = kotlin.io.path.createTempDirectory("provider-payload").toFile()
        val item = ProviderFeedItem(itemId = "1", title = "Darkest Dungeon", link = "https://example.com/g")
        val folder = ProviderLocalPayload.folder(item, root)
        folder.mkdirs()
        java.io.File(folder, "setup.exe").writeBytes(ByteArray(8))
        assertTrue(ProviderLocalPayload.hasInstaller(item, root))
        val ready = TransferJob(
            jobId = "j",
            tabId = "t",
            itemId = "1",
            title = item.title,
            state = TransferState.READY,
            selectedLink = item.link,
            finalPath = folder.absolutePath,
        )
        assertTrue(!ProviderGameUi.isInstalled(ready, item))
        assertTrue(ProviderGameUi.canInstall(ready, item))
        java.io.File(folder, "DarkestDungeon.exe").writeBytes(ByteArray(8))
        assertTrue(ProviderGameUi.isInstalled(ready, item))
        assertTrue(!ProviderGameUi.canInstall(ready, item))
        val failed = ready.copy(state = TransferState.FAILED, errorMessage = "Downloaded file is missing")
        assertEquals("Ready to install", ProviderGameUi.statusLabel(failed, hasLocalInstaller = true))
        assertEquals("", ProviderGameUi.visibleError(failed, hasLocalInstaller = true))
        val verifying = ready.copy(state = TransferState.VERIFYING)
        java.io.File(folder, "DarkestDungeon.exe").delete()
        assertTrue(!ProviderGameUi.isBusy(verifying))
        assertTrue(ProviderGameUi.canInstall(verifying, item))
        assertEquals("Ready to install", ProviderGameUi.statusLabel(verifying, hasLocalInstaller = true))
        root.deleteRecursively()
    }
}
