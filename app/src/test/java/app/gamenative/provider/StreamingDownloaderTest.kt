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
}
