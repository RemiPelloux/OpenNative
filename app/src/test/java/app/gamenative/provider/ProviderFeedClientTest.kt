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

class ProviderFeedClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ProviderFeedClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = ProviderFeedClient(
            httpClient = OkHttpClient(),
            allowLoopbackHttp = true,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetches json and honors etag`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("ETag", "\"v1\"")
                .setHeader("Content-Type", "application/json")
                .setBody("""{"version":1,"items":[{"id":"1","title":"A","link":"https://example.com/a"}]}"""),
        )
        val page = client.fetch(server.url("/feed").toString())
        assertEquals(1, page.items.size)
        assertEquals("\"v1\"", page.etag)

        server.enqueue(MockResponse().setResponseCode(304))
        val cached = client.fetch(server.url("/feed").toString(), etag = "\"v1\"")
        assertTrue(cached.notModified)
    }

    @Test
    fun `appends wordpress paging query params`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("X-WP-TotalPages", "5")
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"id":1,"title":{"rendered":"One"},"link":"https://example.com/one"}]"""),
        )
        val page = client.fetch(
            url = server.url("/wp-json/wp/v2/posts").toString(),
            page = 2,
            perPage = 25,
            orderBy = "date",
            order = "desc",
        )
        assertEquals(1, page.items.size)
        assertEquals(5, page.totalPages)
        val request = server.takeRequest()
        assertEquals("2", request.requestUrl?.queryParameter("page"))
        assertEquals("25", request.requestUrl?.queryParameter("per_page"))
        assertEquals("date", request.requestUrl?.queryParameter("orderby"))
    }

    @Test
    fun `treats later page 404 as the end of the catalog`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val page = client.fetch(server.url("/page/9/").toString(), page = 9)
        assertTrue(page.items.isEmpty())
        assertEquals(9, page.page)
    }

    @Test
    fun `maps 429 to rate limit`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        try {
            client.fetch(server.url("/feed").toString())
            throw AssertionError("expected failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderErrorCode.RATE_LIMIT, error.code)
        }
    }
}
