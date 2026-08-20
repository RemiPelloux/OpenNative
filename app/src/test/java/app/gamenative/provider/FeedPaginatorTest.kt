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
        assertTrue(url.contains("_fields=id,slug,link,title,excerpt,jetpack_featured_media_url"))
        assertFalse(url.contains("content"))
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
    fun `rejects unknown orderby values`() {
        val url = FeedPaginator.apply(
            rawUrl = "https://example.com/wp-json/wp/v2/posts",
            request = FeedPageRequest(page = 1, orderBy = "content;drop"),
            style = PaginationStyle.WORDPRESS_REST,
        )
        assertTrue(url.contains("orderby=date"))
        assertFalse(url.contains("content"))
    }
}
