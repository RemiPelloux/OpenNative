package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPaginatorTest {
    @Test
    fun `wordpress rest uses page per_page orderby order`() {
        val url = FeedPaginator.apply(
            rawUrl = "https://example.com/wp-json/wp/v2/posts",
            request = FeedPageRequest(page = 3, perPage = 50, orderBy = "title", order = "asc"),
            style = PaginationStyle.WORDPRESS_REST,
        )
        assertTrue(url.contains("page=3"))
        assertTrue(url.contains("per_page=50"))
        assertTrue(url.contains("orderby=title"))
        assertTrue(url.contains("order=asc"))
        assertTrue(url.contains("_fields=id,slug,link,title,excerpt,content,jetpack_featured_media_url,featured_media"))
        assertFalse(url.contains("password"))
    }

    @Test
    fun `wordpress rss uses paged and page`() {
        val url = FeedPaginator.apply(
            rawUrl = "https://example.com/feed/",
            request = FeedPageRequest(page = 2),
            style = PaginationStyle.WORDPRESS_RSS,
        )
        assertTrue(url.contains("paged=2"))
        assertTrue(url.contains("page=2"))
        assertTrue(url.contains("orderby=date"))
        assertTrue(url.contains("order=desc"))
    }

    @Test
    fun `replaces existing paging params instead of duplicating them`() {
        val url = FeedPaginator.apply(
            rawUrl = "https://example.com/wp-json/wp/v2/posts?page=1&per_page=10&foo=bar",
            request = FeedPageRequest(page = 2, perPage = 100),
            style = PaginationStyle.WORDPRESS_REST,
        )
        assertEquals(1, Regex("""(?:^|[?&])page=""").findAll(url).count())
        assertEquals(1, Regex("""(?:^|[?&])per_page=""").findAll(url).count())
        assertTrue(url.contains("foo=bar"))
        assertTrue(url.contains("page=2"))
        assertTrue(url.contains("per_page=100"))
    }

    @Test
    fun `detects wordpress rest from the path`() {
        assertEquals(
            PaginationStyle.WORDPRESS_REST,
            FeedPaginator.detectStyle("https://blog.example/wp-json/wp/v2/posts", FeedKind.JSON),
        )
        assertEquals(
            PaginationStyle.WORDPRESS_RSS,
            FeedPaginator.detectStyle("https://blog.example/feed/", FeedKind.RSS),
        )
        assertEquals(
            PaginationStyle.SKIDROW_RSS,
            FeedPaginator.detectStyle(
                "https://feeds.feedburner.com/SkidrowReloadedGames",
                FeedKind.RSS,
            ),
        )
        assertEquals(
            PaginationStyle.SINGLE_DOCUMENT,
            FeedPaginator.detectStyle(
                "https://feeds.feedburner.com/OtherGames",
                FeedKind.RSS,
            ),
        )
    }

    @Test
    fun `feedburner rss stays a single document`() {
        val url = FeedPaginator.apply(
            rawUrl = "https://feeds.feedburner.com/OtherGames",
            request = FeedPageRequest(page = 2, search = "portal"),
            style = PaginationStyle.SINGLE_DOCUMENT,
        )
        assertEquals("https://feeds.feedburner.com/OtherGames", url)
        assertFalse(
            FeedPaginator.hasMore(
                fetchedPage = 1,
                itemCount = 25,
                perPage = 20,
                totalPages = null,
                nextCursor = null,
                style = PaginationStyle.SINGLE_DOCUMENT,
            ),
        )
    }

    @Test
    fun `skidrow browse uses html archive pages and search uses rss`() {
        val first = FeedPaginator.apply(
            rawUrl = "https://feeds.feedburner.com/SkidrowReloadedGames",
            request = FeedPageRequest(page = 1),
            style = PaginationStyle.SKIDROW_RSS,
        )
        assertEquals("https://www.skidrowreloaded.com/", first)
        val next = FeedPaginator.apply(
            rawUrl = "https://feeds.feedburner.com/SkidrowReloadedGames",
            request = FeedPageRequest(page = 2),
            style = PaginationStyle.SKIDROW_RSS,
        )
        assertEquals("https://www.skidrowreloaded.com/page/2/", next)
        val search = FeedPaginator.apply(
            rawUrl = "https://feeds.feedburner.com/SkidrowReloadedGames",
            request = FeedPageRequest(page = 2, search = "portal"),
            style = PaginationStyle.SKIDROW_RSS,
        )
        assertEquals("https://www.skidrowreloaded.com/?s=portal&feed=rss2&paged=2", search)
        assertFalse(search.contains("wp-json"))
        assertTrue(
            FeedPaginator.hasMore(
                fetchedPage = 1,
                itemCount = 9,
                perPage = 20,
                totalPages = null,
                nextCursor = null,
                style = PaginationStyle.SKIDROW_RSS,
            ),
        )
        assertFalse(
            FeedPaginator.hasMore(
                fetchedPage = 2,
                itemCount = 3,
                perPage = 20,
                totalPages = null,
                nextCursor = null,
                style = PaginationStyle.SKIDROW_RSS,
            ),
        )
    }

    @Test
    fun `has more uses total pages or a full page`() {
        assertTrue(FeedPaginator.hasMore(1, 100, 100, totalPages = 4, nextCursor = null))
        assertFalse(FeedPaginator.hasMore(4, 10, 100, totalPages = 4, nextCursor = null))
        assertTrue(FeedPaginator.hasMore(1, 10, 100, totalPages = null, nextCursor = "next"))
    }

    @Test
    fun `adds wordpress search query`() {
        val rest = FeedPaginator.apply(
            rawUrl = "https://example.com/wp-json/wp/v2/posts",
            request = FeedPageRequest(page = 1, search = "portal 2"),
            style = PaginationStyle.WORDPRESS_REST,
        )
        assertTrue(rest.contains("search=portal%202"))
        val rss = FeedPaginator.apply(
            rawUrl = "https://example.com/feed/",
            request = FeedPageRequest(page = 1, search = "portal"),
            style = PaginationStyle.WORDPRESS_RSS,
        )
        assertTrue(rss.contains("s=portal"))
    }

    @Test
    fun `strips wordpress embed so responses stay public fields only`() {
        val url = FeedPaginator.apply(
            rawUrl = "https://blog.example/wp-json/wp/v2/posts?per_page=100&page=1&_embed=1",
            request = FeedPageRequest(page = 2, perPage = 100),
            style = PaginationStyle.WORDPRESS_REST,
        )
        assertFalse(url.contains("_embed"))
        assertEquals("https://blog.example/wp-json/wp/v2/posts", FeedPaginator.canonicalFeedUrl(
            "https://blog.example/wp-json/wp/v2/posts?per_page=100&page=1&orderby=date&order=desc&_embed=1",
        ))
        assertTrue(url.contains("_fields="))
    }

    @Test
    fun `rejects unknown orderby values`() {
        val url = FeedPaginator.apply(
            rawUrl = "https://example.com/wp-json/wp/v2/posts",
            request = FeedPageRequest(page = 1, orderBy = "content;drop"),
            style = PaginationStyle.WORDPRESS_REST,
        )
        assertTrue(url.contains("orderby=date"))
        assertFalse(url.contains("content;drop"))
    }
}
