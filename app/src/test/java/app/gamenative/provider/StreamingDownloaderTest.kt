package app.gamenative.provider

import java.io.File
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StreamingDownloaderTest {
    @get:Rule
    val temp = TemporaryFolder()
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `resumes from existing partial bytes`() {
        val partial = File(temp.newFolder(), "game.zip.partial")
        partial.writeBytes(byteArrayOf(1, 2, 3))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Length", "2")
                .setBody(Buffer().write(byteArrayOf(4, 5))),
        )
        val downloader = StreamingDownloader(OkHttpClient())
        val file = downloader.download(server.url("/file").toString(), partial, expectedBytes = 5)
        val promoted = downloader.promote(file)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), promoted.readBytes().toList())
    }

    @Test
    fun `restarts from zero when host ignores range`() {
        val partial = File(temp.newFolder(), "game.zip.partial")
        partial.writeBytes(byteArrayOf(1, 2, 3))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "5")
                .setBody(Buffer().write(byteArrayOf(4, 5, 6, 7, 8))),
        )

        val downloader = StreamingDownloader(OkHttpClient())
        val file = downloader.download(server.url("/file").toString(), partial, expectedBytes = 5)

        assertEquals(listOf<Byte>(4, 5, 6, 7, 8), file.readBytes().toList())
        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `uses a complete partial without another request`() {
        val partial = File(temp.newFolder(), "game.zip.partial")
        partial.writeBytes(byteArrayOf(1, 2, 3))

        val file = StreamingDownloader(OkHttpClient()).download(
            server.url("/file").toString(),
            partial,
            expectedBytes = 3,
        )

        assertEquals(listOf<Byte>(1, 2, 3), file.readBytes().toList())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `restarts an oversized stale partial`() {
        val partial = File(temp.newFolder(), "game.zip.partial")
        partial.writeBytes(byteArrayOf(1, 2, 3))
        server.enqueue(MockResponse().setBody(Buffer().write(byteArrayOf(4, 5))))

        val file = StreamingDownloader(OkHttpClient()).download(
            server.url("/file").toString(),
            partial,
            expectedBytes = 2,
        )

        assertEquals(listOf<Byte>(4, 5), file.readBytes().toList())
        assertEquals(null, server.takeRequest().getHeader("Range"))
    }
}
