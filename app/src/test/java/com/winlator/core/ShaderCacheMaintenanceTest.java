package com.winlator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.winlator.container.Container;
import com.winlator.core.envvars.EnvVars;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
public class ShaderCacheMaintenanceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void pruningKeepsActiveAndUnrelatedFiles() throws Exception {
        Container container = new Container("maintenance");
        File root = temporaryFolder.newFolder("container");
        container.setRootDir(root);
        ShaderCacheManager.CachePaths active = ShaderCacheManager.prepare(container, new EnvVars());
        File activeFile = new File(active.hostDxvkDirectory(), "active.cache");
        Files.write(activeFile.toPath(), new byte[] {1, 2, 3});

        File inactive = new File(
                new File(new File(root, ".cache/opennative-shaders/backends"), "dxvk"),
                "0123456789abcdef"
        );
        assertTrue(inactive.mkdirs());
        Files.write(new File(inactive, "old.cache").toPath(), new byte[128]);
        File unrelated = new File(root, "save.dat");
        Files.write(unrelated.toPath(), new byte[] {9});

        ShaderCacheManager.CacheHealth before = ShaderCacheManager.inspectHealth(container);
        assertEquals(1, before.inactiveGenerations());
        ShaderCacheManager.CacheMaintenanceResult result =
                ShaderCacheManager.pruneInactive(container, 3, 0);

        assertEquals(1, result.removedGenerations());
        assertFalse(inactive.exists());
        assertTrue(activeFile.isFile());
        assertTrue(unrelated.isFile());
    }
}
