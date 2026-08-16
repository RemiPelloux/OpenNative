package app.gamenative.performance.runtime

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentInstallPolicyTest {
    @Test
    fun `matching marker and healthy files skip install`() {
        val root = Files.createTempDirectory("component-policy").toFile()
        val marker = File(root, "component.marker")
        val critical = File(root, "component.so").apply { writeBytes(byteArrayOf(1)) }

        assertTrue(ComponentInstallPolicy.markInstalled(marker, "driver-1"))
        assertFalse(ComponentInstallPolicy.needsInstall(marker, "driver-1", listOf(critical)))
    }

    @Test
    fun `version change or damaged file triggers repair`() {
        val root = Files.createTempDirectory("component-policy-repair").toFile()
        val marker = File(root, "component.marker")
        val critical = File(root, "component.so").apply { writeBytes(byteArrayOf(1)) }
        ComponentInstallPolicy.markInstalled(marker, "driver-1")

        assertTrue(ComponentInstallPolicy.needsInstall(marker, "driver-2", listOf(critical)))
        critical.writeBytes(byteArrayOf())
        assertTrue(ComponentInstallPolicy.needsInstall(marker, "driver-1", listOf(critical)))
    }

    @Test
    fun `blank fingerprints and empty manifests fail closed`() {
        val root = Files.createTempDirectory("component-policy-invalid").toFile()
        val marker = File(root, "component.marker")

        assertTrue(ComponentInstallPolicy.needsInstall(marker, "", emptyList()))
        assertFalse(ComponentInstallPolicy.markInstalled(marker, ""))
    }
}
