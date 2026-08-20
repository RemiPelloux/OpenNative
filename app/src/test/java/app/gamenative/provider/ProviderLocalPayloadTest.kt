package app.gamenative.provider

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLocalPayloadTest {
    @Test
    fun `uses the game folder when the job path is missing`() {
        val root = kotlin.io.path.createTempDirectory("provider-local").toFile()
        val item = ProviderFeedItem(
            itemId = "1",
            title = "Darkest Dungeon The Collectors Edition v27760  7 DLCsBonuses",
            link = "https://example.com/g",
        )
        val folder = ProviderLocalPayload.folder(item, root)
        val nested = File(folder, "Darkest Dungeon FitGirl Repack")
        nested.mkdirs()
        File(nested, "setup.exe").writeBytes(ByteArray(8))
        File(nested, "fg-01.bin").writeBytes(ByteArray(8))
        val job = TransferJob(
            jobId = "j",
            tabId = "t",
            itemId = "1",
            title = item.title,
            state = TransferState.FAILED,
            selectedLink = item.link,
            finalPath = "",
            errorMessage = "Downloaded file is missing",
        )
        val resolved = ProviderLocalPayload.resolve(job, item, root)
        assertNotNull(resolved)
        assertEquals(folder.absolutePath, resolved?.absolutePath)
        assertTrue(ProviderLocalPayload.hasInstaller(item, root))
        val setup = ProviderLocalPayload.findInstaller(folder)
        assertNotNull(setup)
        assertEquals("setup.exe", setup?.name)
        assertTrue(!ProviderLocalPayload.hasGameExe(item, root))
        assertNull(
            ProviderLocalPayload.resolve(
                job,
                item.copy(title = "Other Game"),
                root,
            ),
        )
        root.deleteRecursively()
    }

    @Test
    fun `slugs installer pack folders before Wine`() {
        val root = kotlin.io.path.createTempDirectory("provider-pack").toFile()
        val pack = File(root, "Darkest Dungeon [FitGirl Repack]").also { it.mkdirs() }
        File(pack, "setup.exe").writeBytes(ByteArray(8))
        val moved = ProviderLocalPayload.relocatePack(pack)
        assertEquals("darkest-dungeon-fitgirl-repack", moved.name)
        assertTrue(File(moved, "setup.exe").exists())
        root.deleteRecursively()
    }
}
