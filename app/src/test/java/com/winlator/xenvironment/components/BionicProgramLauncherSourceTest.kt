package com.winlator.xenvironment.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BionicProgramLauncherSourceTest {
    @Test
    fun `evshim path follows the installed package files directory`() {
        val source = File(
            "src/main/java/com/winlator/xenvironment/components/BionicProgramLauncherComponent.java",
        ).readText()

        assertTrue(source.contains("envVars.put(\"EVSHIM_BASE_PATH\", context.getFilesDir().getAbsolutePath())"))
        assertFalse(source.contains("/data/data/app.gamenative/files/imagefs/tmp/gamepad"))
    }
}
