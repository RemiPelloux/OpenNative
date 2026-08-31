package app.gamenative.provider

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrphanStagingScannerTest {
    @Test
    fun `skips active jobs and recent folders`() {
        val root = createTempDir(prefix = "staging-root")
        val orphan = File(root, "job-old").apply {
            mkdirs()
            File(this, "payload.bin").writeText("x")
            setLastModified(1_000L)
        }
        val recent = File(root, "job-new").apply {
            mkdirs()
            setLastModified(System.currentTimeMillis())
        }
        val busy = File(root, "job-busy").apply {
            mkdirs()
            setLastModified(1_000L)
        }
        val found = OrphanStagingScanner.scan(
            stagingRoot = root,
            activeJobIds = setOf("job-busy"),
            nowMs = 1_000L + OrphanStagingScanner.MIN_AGE_MS + 1,
        )
        assertEquals(listOf(orphan.absolutePath), found.map { it.path })
        assertTrue(recent.exists())
        assertTrue(busy.exists())
        OrphanStagingScanner.remove(found)
        assertTrue(!orphan.exists())
    }
}
