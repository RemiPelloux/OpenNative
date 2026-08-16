package app.gamenative.powercontrol.metrics

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonlSessionLogTest {
    @Test
    fun `append batches disk flushes and close persists pending lines`() {
        val directory = Files.createTempDirectory("metrics-log-test").toFile()
        val log = JsonlSessionLog(
            tag = "JsonlSessionLogTest",
            filePrefix = "session-",
            flushEveryLines = 3,
        )

        try {
            log.open(directory, 123L)
            val output = directory.resolve("session-123.jsonl")

            log.append("one")
            log.append("two")
            assertEquals("", output.readText())

            log.append("three")
            assertEquals(listOf("one", "two", "three"), output.readLines())

            log.append("four")
            log.close()
            assertEquals(listOf("one", "two", "three", "four"), output.readLines())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `byte cap stops later writes`() {
        val directory = Files.createTempDirectory("metrics-cap-test").toFile()
        val log = JsonlSessionLog(
            tag = "JsonlSessionLogTest",
            filePrefix = "session-",
            maxLogBytes = 4,
            flushEveryLines = 1,
        )

        try {
            log.open(directory, 456L)
            log.append("abcd")
            log.append("ignored")
            log.close()

            val lines = directory.resolve("session-456.jsonl").readLines()
            assertEquals(listOf("abcd"), lines)
            assertTrue(lines.none { it.contains("ignored") })
        } finally {
            directory.deleteRecursively()
        }
    }
}
