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
        val state = client.validateCredential("secret-key")
        assertTrue(state.valid)
        assertEquals("remi", state.username)
        val request = server.takeRequest()
        assertEquals("Bearer secret-key", request.getHeader("Authorization"))
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
    fun `maps unsupported host without leaking the key`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"error","error":{"code":"LINK_HOST_NOT_SUPPORTED","message":"Host not supported"}}""",
            ),
        )
        try {
            client.resolve("secret-key", "https://example.com/post")
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
        val resolved = client.resolve("secret-key", "https://example.com/file.zip")
        assertEquals("game.zip", resolved.filename)
        val request = server.takeRequest()
        assertEquals("https://example.com/file.zip", request.requestUrl?.queryParameter("link"))
    }
}
