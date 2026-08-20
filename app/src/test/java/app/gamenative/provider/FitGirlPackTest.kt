package app.gamenative.provider

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FitGirlPackTest {
    @Test
    fun `detects FreeArc FitGirl bins next to setup`() {
        val root = kotlin.io.path.createTempDirectory("fitgirl-pack").toFile()
        File(root, "setup.exe").writeBytes(ByteArray(8))
        File(root, "fg-01.bin").writeBytes(byteArrayOf(0x41, 0x72, 0x43, 0x01, 0, 0, 6, 7))
        assertTrue(FitGirlPack.isPack(root))
        assertTrue(FitGirlPack.isArcBin(File(root, "fg-01.bin")))
        assertFalse(FitGirlPack.isArcBin(File(root, "setup.exe")))
        assertEquals(
            "ZINK=1 WINEDLLOVERRIDES=isdone,unarc=n,b WINE_LARGE_ADDRESS_AWARE=0 TEMP=C:/windows/temp TMP=C:/windows/temp",
            FitGirlPack.mergeEnv("ZINK=1"),
        )
        root.deleteRecursively()
    }
}
