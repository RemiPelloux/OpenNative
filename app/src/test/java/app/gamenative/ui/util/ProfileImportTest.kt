package app.gamenative.ui.util

import com.winlator.container.ContainerData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfileImportTest {
    @Test
    fun `preview groups diffs and keeps unsupported keys out of merge`() {
        val current = PortableContainerProfile.export(ContainerData(screenSize = "1280x720"), false, 60)
        val incoming = JSONObject(current.toString())
            .put("screenSize", "1920x1080")
            .put("futureOnly", "keep-me")
        val plan = ProfileImport.preview(current, incoming)
        assertTrue(plan.diffs.any { it.key == "screenSize" && it.category == ProfileCategory.DISPLAY })
        assertTrue(plan.unsupported.contains("futureOnly"))
        val merged = ProfileImport.merge(
            current,
            incoming,
            setOf(ProfileCategory.DISPLAY),
            replace = true,
        )
        assertEquals("1920x1080", merged.getString("screenSize"))
        assertTrue(!merged.has("futureOnly"))
    }

    @Test
    fun `backup round trips and rollback restores the previous profile`() {
        val dir = createTempDir(prefix = "profile-backup")
        val original = PortableContainerProfile.export(ContainerData(screenSize = "800x600"), true, 30)
        val file = ProfileImport.backupFile(dir, "app/1")
        ProfileImport.writeBackup(file, original)
        val restored = ProfileImport.readBackup(file)
        assertEquals("800x600", restored?.getString("screenSize"))
        assertEquals(30, restored?.getInt("fpsLimiterTarget"))
    }
}
