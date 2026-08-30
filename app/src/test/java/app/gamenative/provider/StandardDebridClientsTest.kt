package app.gamenative.provider

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StandardDebridClientsTest {
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
    fun `real debrid validates and unlocks with bearer auth`() = runBlocking {
        val client = RealDebridClient(OkHttpClient(), server.url("/rest/1.0").toString().trimEnd('/'))
        server.enqueue(MockResponse().setBody("""{"id":7,"username":"remi"}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"filename":"game.zip","download":"https://cdn.example/game.zip","filesize":42}""",
            ),
        )
        assertEquals("remi", client.validateCredential(" token ").username)
        assertEquals("game.zip", client.resolve("token", "https://host.example/file", "pass").filename)
        val account = server.takeRequest()
        assertEquals("Bearer token", account.getHeader("Authorization"))
        assertEquals("/rest/1.0/user", account.requestUrl?.encodedPath)
        val unlock = server.takeRequest()
        assertEquals("POST", unlock.method)
        assertEquals("link=https%3A%2F%2Fhost.example%2Ffile&password=pass", unlock.body.readUtf8())
    }

    @Test
    fun `real debrid adds selects and exposes magnet files`() = runBlocking {
        val client = RealDebridClient(OkHttpClient(), server.url("/rest/1.0").toString().trimEnd('/'))
        server.enqueue(MockResponse().setBody("""{"id":"torrent-7","uri":"https://example"}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(
            MockResponse().setBody(
                """{"status":"downloaded","files":[{"id":1,"path":"/setup.exe","bytes":99,"selected":1}],"links":["https://host.example/restricted"]}""",
            ),
        )
        val uploaded = client.uploadMagnet("token", "magnet:?xt=urn:btih:abc")
        assertEquals("torrent-7", uploaded.id)
        val add = server.takeRequest()
        assertEquals("magnet=magnet%3A%3Fxt%3Durn%3Abtih%3Aabc", add.body.readUtf8())
        val select = server.takeRequest()
        assertEquals("/rest/1.0/torrents/selectFiles/torrent-7", select.requestUrl?.encodedPath)
        assertEquals("files=all", select.body.readUtf8())
        val files = client.magnetFiles("token", uploaded.id)
        assertEquals("setup.exe", files.single().relativePath)
        assertEquals(99L, files.single().sizeBytes)
    }

    @Test
    fun `premiumize uses bearer auth and direct download content`() = runBlocking {
        val client = PremiumizeClient(OkHttpClient(), server.url("/api").toString().trimEnd('/'))
        server.enqueue(MockResponse().setBody("""{"status":"success","customer_id":"123"}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"status":"success","content":[{"path":"game.rar","size":21,"link":"https://cdn.example/game.rar"}]}""",
            ),
        )
        assertTrue(client.validateCredential("token").valid)
        assertEquals(21L, client.resolve("token", "https://host.example/file").sizeBytes)
        server.takeRequest()
        val unlock = server.takeRequest()
        assertEquals("Bearer token", unlock.getHeader("Authorization"))
        assertEquals("src=https%3A%2F%2Fhost.example%2Ffile", unlock.body.readUtf8())
    }

    @Test
    fun `debrid link maps its value envelope`() = runBlocking {
        val client = DebridLinkClient(OkHttpClient(), server.url("/api/v2").toString().trimEnd('/'))
        server.enqueue(MockResponse().setBody("""{"success":true,"value":{"username":"remi"}}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"value":{"name":"game.iso","downloadUrl":"https://cdn.example/game.iso","size":84}}""",
            ),
        )
        assertEquals("remi", client.validateCredential("token").username)
        assertEquals("game.iso", client.resolve("token", "https://host.example/file").filename)
        server.takeRequest()
        val unlock = server.takeRequest()
        assertEquals("/api/v2/downloader/add", unlock.requestUrl?.encodedPath)
        assertEquals("application/json; charset=utf-8", unlock.getHeader("Content-Type"))
        assertTrue(unlock.body.readUtf8().contains("https://host.example/file"))
    }

    @Test
    fun `torbox waits for web download and requests the file`() = runBlocking {
        val client = TorBoxClient(
            OkHttpClient(),
            server.url("/v1/api").toString().trimEnd('/'),
            pollDelayMs = 1,
            pollAttempts = 2,
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"email":"r@example.com"}}"""))
        server.enqueue(MockResponse().setBody("""{"success":true,"data":{"webdownload_id":9}}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"success":true,"data":[{"download_finished":true,"files":[{"id":3,"name":"game.zip","size":12}]}]}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"success":true,"data":"https://cdn.example/game.zip"}"""))
        assertTrue(client.validateCredential("token").valid)
        assertEquals("game.zip", client.resolve("token", "https://host.example/file").filename)
        server.takeRequest()
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("link=https%3A%2F%2Fhost.example%2Ffile", create.body.readUtf8())
        server.takeRequest()
        val requestLink = server.takeRequest()
        assertEquals("9", requestLink.requestUrl?.queryParameter("web_id"))
        assertEquals("3", requestLink.requestUrl?.queryParameter("file_id"))
    }

    @Test
    fun `all new providers map unauthorized responses`() = runBlocking {
        val clients = listOf<DebridResolver>(
            RealDebridClient(OkHttpClient(), server.url("/rd").toString().trimEnd('/')),
            PremiumizeClient(OkHttpClient(), server.url("/pm").toString().trimEnd('/')),
            DebridLinkClient(OkHttpClient(), server.url("/dl").toString().trimEnd('/')),
            TorBoxClient(OkHttpClient(), server.url("/tb").toString().trimEnd('/')),
        )
        clients.forEach { resolver ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            try {
                resolver.validateCredential("bad")
                throw AssertionError("expected authentication failure")
            } catch (error: ProviderException) {
                assertEquals(ProviderErrorCode.AUTHENTICATION, error.code)
                assertTrue(!error.message.orEmpty().contains("bad"))
            }
        }
    }
}
