package app.gamenative.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubReleaseUpdaterTest {
    @Test
    fun `selects the standard modern apk and digest`() {
        val info = GitHubReleaseUpdater.parseRelease(
            """{
              "tag_name":"opennative-v1.4.2",
              "draft":false,
              "prerelease":false,
              "body":"Fixes",
              "assets":[
                {"name":"OpenNative-1.4.2-modern-xr.apk","browser_download_url":"https://github.com/RemiPelloux/OpenNative/releases/download/opennative-v1.4.2/xr.apk"},
                {"name":"OpenNative-1.4.2-modern.apk","browser_download_url":"https://github.com/RemiPelloux/OpenNative/releases/download/opennative-v1.4.2/modern.apk","digest":"sha256:${"a".repeat(64)}"}
              ]
            }""",
        )
        assertEquals("1.4.2", info.versionName)
        assertTrue(info.downloadUrl.endsWith("modern.apk"))
        assertEquals("a".repeat(64), info.sha256)
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects assets outside the OpenNative release path`() {
        GitHubReleaseUpdater.parseRelease(
            """{"tag_name":"1.4.2","assets":[{"name":"build-modern.apk","browser_download_url":"https://example.com/update.apk"}]}""",
        )
    }

    @Test
    fun `compares numeric release versions`() {
        assertTrue(GitHubReleaseUpdater.isNewerVersion("1.4.2", "1.4.1"))
        assertTrue(GitHubReleaseUpdater.isNewerVersion("1.10.0", "1.9.9"))
        assertFalse(GitHubReleaseUpdater.isNewerVersion("1.4.1", "1.4.2"))
        assertFalse(GitHubReleaseUpdater.isNewerVersion("1.4.2", "1.4.2"))
    }

    @Test
    fun `checks at most once per day and recovers from clock changes`() {
        val day = 24L * 60L * 60L * 1_000L
        assertTrue(GitHubReleaseUpdater.shouldCheck(0L, 100L))
        assertFalse(GitHubReleaseUpdater.shouldCheck(100L, 100L + day - 1L))
        assertTrue(GitHubReleaseUpdater.shouldCheck(100L, 100L + day))
        assertTrue(GitHubReleaseUpdater.shouldCheck(200L, 100L))
    }
}
