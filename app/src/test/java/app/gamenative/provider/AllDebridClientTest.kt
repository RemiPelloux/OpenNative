package app.gamenative.provider

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AllDebridClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: AllDebridClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = AllDebridClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
            allowLoopbackHttp = true,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `validates account without leaking the key`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"status":"success","data":{"user":{"username":"remi"}}}"""),
        )
        val state = client.validateCredential("  secret-key\n")
        assertTrue(state.valid)
        assertEquals("remi", state.username)
        val request = server.takeRequest()
        assertEquals("Bearer secret-key", request.getHeader("Authorization"))
        assertEquals("GET", request.method)
        assertTrue(request.requestUrl?.queryParameter("apikey") == null)
    }

    @Test
    fun `maps bad key to authentication`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"status":"error","error":{"code":"AUTH_BAD_APIKEY"}}"""),
        )
        try {
            client.validateCredential("bad")
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.AUTHENTICATION, error.code)
            assertTrue(!error.message.orEmpty().contains("bad"))
            assertTrue(error.message.orEmpty().contains("authentication"))
        }
    }

    @Test
    fun `rejects a success response without an account`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"success","data":{}}"""))

        try {
            client.validateCredential("secret-key")
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.MALFORMED_RESPONSE, error.code)
        }
    }

    @Test
    fun `maps unsupported host without leaking the key`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"error","error":{"code":"LINK_HOST_NOT_SUPPORTED","message":"Host not supported"}}""",
            ),
        )
        try {
            client.resolve("secret-key", "https://example.com/post", "")
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.UNSUPPORTED_HOST, error.code)
            assertTrue(!error.message.orEmpty().contains("secret-key"))
            assertTrue(error.message.orEmpty().contains("not supported"))
        }
    }

    @Test
    fun `unlocks only the user selected https link`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"success","data":{"filename":"game.zip","link":"https://cdn.example/file","filesize":10}}""",
            ),
        )
        val resolved = client.resolve("secret-key", "https://1fichier.com/?abcd1234", "hunter2")
        assertEquals("game.zip", resolved.filename)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("link=https%3A%2F%2F1fichier.com%2F%3Fabcd1234&password=hunter2", request.body.readUtf8())
        assertTrue(request.requestUrl?.queryParameter("link") == null)
    }

    @Test
    fun `maps a password protected unlock`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"error","error":{"code":"LINK_PASS_PROTECTED","message":"Link is password protected"}}""",
            ),
        )
        try {
            client.resolve("secret-key", "https://1fichier.com/?abcd1234", "")
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.PASSWORD_PROTECTED, error.code)
            assertTrue(error.message.orEmpty().contains("password"))
        }
    }

    @Test
    fun `polls delayed unlock until a file link is ready`() = runBlocking {
        client = AllDebridClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
            allowLoopbackHttp = true,
            delayedPollMs = 1L,
            delayedAttempts = 4,
        )
        server.enqueue(
            MockResponse().setBody(
                """{"status":"success","data":{"filename":"game.rar","filesize":20,"delayed":99}}""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"status":"success","data":{"status":1,"time_left":5}}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"status":"success","data":{"status":2,"time_left":0,"link":"https://cdn.example/file"}}""",
            ),
        )
        val resolved = client.resolve("secret-key", "https://datanodes.to/file.rar", "")
        assertEquals("game.rar", resolved.filename)
        assertEquals("https://cdn.example/file", resolved.url)
        assertEquals(3, server.requestCount)
        assertEquals("POST", server.takeRequest().method)
        assertEquals("POST", server.takeRequest().method)
        val delayed = server.takeRequest()
        assertEquals("POST", delayed.method)
        assertEquals("id=99", delayed.body.readUtf8())
    }

    @Test
    fun `maps http 429 to rate limit without leaking the key`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("too many"))
        try {
            client.validateCredential("secret-key")
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.RATE_LIMIT, error.code)
            assertTrue(!error.message.orEmpty().contains("secret-key"))
        }
    }

    @Test
    fun `maps a request timeout without leaking the key`() = runBlocking {
        client = AllDebridClient(
            httpClient = OkHttpClient.Builder()
                .callTimeout(50, TimeUnit.MILLISECONDS)
                .build(),
            baseUrl = server.url("/").toString().trimEnd('/'),
            allowLoopbackHttp = true,
        )
        server.enqueue(
            MockResponse()
                .setHeadersDelay(400, TimeUnit.MILLISECONDS)
                .setBody("""{"status":"success","data":{"user":{"username":"remi"}}}"""),
        )
        try {
            client.validateCredential("secret-key")
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.TIMEOUT, error.code)
            assertTrue(!error.message.orEmpty().contains("secret-key"))
        }
    }

    @Test
    fun `uploads a magnet and lists ready files`() = runBlocking {
        client = AllDebridClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString().trimEnd('/'),
            allowLoopbackHttp = true,
            magnetPollMs = 1L,
            magnetAttempts = 3,
        )
        server.enqueue(
            MockResponse().setBody(
                """{"status":"success","data":{"magnets":[{"id":77,"ready":true,"name":"Game","size":30}]}}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"status":"success","data":{"magnets":[{"id":77,"files":[{"n":"game.rar","s":30,"l":"https://alldebrid.com/f/a"}]}]}}""",
            ),
        )
        val uploaded = client.uploadMagnet("secret-key", "magnet:?xt=urn:btih:abc")
        assertEquals("77", uploaded.id)
        val files = client.magnetFiles("secret-key", uploaded.id)
        assertEquals("game.rar", files.single().relativePath)
        assertEquals("https://alldebrid.com/f/a", files.single().link)
    }
}
