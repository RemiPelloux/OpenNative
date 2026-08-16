package com.winlator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.winlator.core.envvars.EnvVars;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;

public class ShaderCacheManagerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preparesGuestVisiblePerContainerCaches() throws Exception {
        ShaderCacheManager.CachePaths paths = paths("CUSTOM_GAME_1");
        EnvVars envVars = new EnvVars();

        ShaderCacheManager.prepare(paths, envVars);

        assertTrue(paths.hostMesaDirectory().isDirectory());
        assertTrue(paths.hostDxvkDirectory().isDirectory());
        assertTrue(paths.hostVkd3dDirectory().isDirectory());
        assertEquals(paths.guestMesaDirectory(), envVars.get("MESA_SHADER_CACHE_DIR"));
        assertEquals(paths.guestDxvkDirectory(), envVars.get("DXVK_STATE_CACHE_PATH"));
        assertEquals(paths.guestVkd3dCacheFile(), envVars.get("VKD3D_SHADER_CACHE_PATH"));
        assertTrue(paths.guestDxvkDirectory().startsWith(paths.hostRoot().getAbsolutePath()));
    }

    @Test
    public void preservesExplicitUserPaths() throws Exception {
        ShaderCacheManager.CachePaths paths = paths("CUSTOM_GAME_2");
        EnvVars envVars = new EnvVars(
                "MESA_SHADER_CACHE_DIR=/custom/mesa " +
                "DXVK_STATE_CACHE_PATH=/custom/dxvk " +
                "VKD3D_SHADER_CACHE_PATH=/custom/vkd3d.cache"
        );

        ShaderCacheManager.prepare(paths, envVars);

        assertEquals("/custom/mesa", envVars.get("MESA_SHADER_CACHE_DIR"));
        assertEquals("/custom/dxvk", envVars.get("DXVK_STATE_CACHE_PATH"));
        assertEquals("/custom/vkd3d.cache", envVars.get("VKD3D_SHADER_CACHE_PATH"));
    }

    @Test
    public void driverOrWrapperVersionCreatesNewGeneration() {
        String baseline = ShaderCacheManager.generationKey(
                "wrapper-v2", "25.1", "version=turnip25.1", "dxvk", "version=2.6.1", "bionic", "proton-9"
        );
        String newDriver = ShaderCacheManager.generationKey(
                "wrapper-v2", "25.2", "version=turnip25.2", "dxvk", "version=2.6.1", "bionic", "proton-9"
        );
        String newWrapper = ShaderCacheManager.generationKey(
                "wrapper-v2", "25.1", "version=turnip25.1", "dxvk", "version=2.7.1", "bionic", "proton-9"
        );

        assertNotEquals(baseline, newDriver);
        assertNotEquals(baseline, newWrapper);
        assertEquals(16, baseline.length());
    }

    @Test
    public void backendGenerationsInvalidateOnlyCompatibleCaches() {
        ShaderCacheManager.BackendGenerations baseline = backendGenerations(
                "turnip25.1", "2.4.1", "2.14.1", "/drivers/turnip25.1"
        );
        ShaderCacheManager.BackendGenerations newDriver = backendGenerations(
                "turnip26.2", "2.4.1", "2.14.1", "/drivers/turnip26.2"
        );
        ShaderCacheManager.BackendGenerations newDxvk = backendGenerations(
                "turnip25.1", "2.6.1", "2.14.1", "/drivers/turnip25.1"
        );
        ShaderCacheManager.BackendGenerations newVkd3d = backendGenerations(
                "turnip25.1", "2.4.1", "2.15.0", "/drivers/turnip25.1"
        );

        assertNotEquals(baseline.mesa(), newDriver.mesa());
        assertEquals(baseline.dxvk(), newDriver.dxvk());
        assertNotEquals(baseline.vkd3d(), newDriver.vkd3d());

        assertEquals(baseline.mesa(), newDxvk.mesa());
        assertNotEquals(baseline.dxvk(), newDxvk.dxvk());
        assertEquals(baseline.vkd3d(), newDxvk.vkd3d());

        assertEquals(baseline.mesa(), newVkd3d.mesa());
        assertEquals(baseline.dxvk(), newVkd3d.dxvk());
        assertNotEquals(baseline.vkd3d(), newVkd3d.vkd3d());
    }

    @Test
    public void effectiveRuntimeDriverCreatesNewGeneration() {
        String selected = ShaderCacheManager.generationKey(
                "wrapper-v2", "25.1", "version=turnip", "dxvk", "version=2.6.1", "bionic", "proton-9",
                "/drivers/Turnip-v26.2-R4\nlibvulkan_freedreno.so"
        );
        String fallback = ShaderCacheManager.generationKey(
                "wrapper-v2", "25.1", "version=turnip", "dxvk", "version=2.6.1", "bionic", "proton-9",
                "\n\n/system/lib64/hw/vulkan.adreno.so"
        );

        assertNotEquals(selected, fallback);
    }

    @Test
    public void glibcUsesRootFsPathWhileBionicUsesHostPath() throws Exception {
        File containerRoot = temporaryFolder.newFolder("runtime-paths");
        ShaderCacheManager.CachePaths bionic = paths(containerRoot, "bionic");
        ShaderCacheManager.CachePaths glibc = paths(containerRoot, "glibc");

        assertTrue(bionic.guestDxvkDirectory().startsWith(containerRoot.getAbsolutePath()));
        assertTrue(glibc.guestDxvkDirectory().startsWith("/home/xuser/.cache/"));
        assertFalse(glibc.guestDxvkDirectory().contains(containerRoot.getAbsolutePath()));
    }

    @Test
    public void reportsAndClearsOnlyManagedCache() throws Exception {
        File containerRoot = temporaryFolder.newFolder("CUSTOM_GAME_3");
        ShaderCacheManager.CachePaths paths = paths(containerRoot);
        ShaderCacheManager.prepare(paths, new EnvVars());
        File cacheFile = new File(paths.hostDxvkDirectory(), "game.dxvk-cache");
        Files.write(cacheFile.toPath(), new byte[] {1, 2, 3, 4});
        File unrelated = new File(containerRoot, "save.dat");
        Files.write(unrelated.toPath(), new byte[] {9});

        ShaderCacheManager.CacheStats stats = ShaderCacheManager.inspectPath(paths.hostRoot());
        assertEquals(4L, stats.bytes());
        assertEquals(1, stats.files());
        File managedRoot = new File(new File(containerRoot, ".cache"), ShaderCacheManager.CACHE_ROOT_NAME);
        assertTrue(ShaderCacheManager.deleteTreeInsideRoot(managedRoot, paths.hostRoot()));
        assertFalse(paths.hostRoot().exists());
        assertTrue(unrelated.isFile());
    }

    @Test
    public void migratesMatchingLegacyCacheWithoutCopying() throws Exception {
        File containerRoot = temporaryFolder.newFolder("CUSTOM_GAME_LEGACY");
        ShaderCacheManager.CachePaths target = paths(containerRoot);
        File managedRoot = new File(new File(containerRoot, ".cache"), ShaderCacheManager.CACHE_ROOT_NAME);
        File legacyRoot = new File(managedRoot, "0123456789abcdef");
        File legacyDxvk = new File(legacyRoot, "dxvk");
        assertTrue(legacyDxvk.mkdirs());
        File stateCache = new File(legacyDxvk, "game.dxvk-cache");
        Files.write(stateCache.toPath(), new byte[] {1, 2, 3});

        ShaderCacheManager.migrateLegacy(containerRoot, "0123456789abcdef", target);

        assertFalse(legacyDxvk.exists());
        assertTrue(new File(target.hostDxvkDirectory(), "game.dxvk-cache").isFile());
    }

    @Test
    public void classifiesColdAndWarmSessionGrowth() {
        ShaderCacheManager.CacheSessionResult cold = ShaderCacheManager.compare(
                new ShaderCacheManager.CacheStats(0, 0, 0),
                new ShaderCacheManager.CacheStats(4096, 2, 100)
        );
        ShaderCacheManager.CacheSessionResult warm = ShaderCacheManager.compare(
                new ShaderCacheManager.CacheStats(4096, 2, 100),
                new ShaderCacheManager.CacheStats(6144, 3, 200)
        );

        assertFalse(cold.warmAtLaunch());
        assertTrue(cold.wroteCache());
        assertEquals(2, cold.addedFiles());
        assertEquals(4096L, cold.addedBytes());
        assertTrue(warm.warmAtLaunch());
        assertEquals(1, warm.addedFiles());
        assertEquals(2048L, warm.addedBytes());
    }

    @Test
    public void reusesValidLaunchSnapshot() throws Exception {
        ShaderCacheManager.CachePaths paths = paths("CUSTOM_GAME_SNAPSHOT");
        ShaderCacheManager.prepare(paths, new EnvVars());
        File cacheFile = new File(paths.hostDxvkDirectory(), "game.dxvk-cache");
        Files.write(cacheFile.toPath(), new byte[] {1, 2, 3, 4});

        ShaderCacheManager.CacheStats scanned = ShaderCacheManager.inspectAndSnapshot(paths);
        cacheFile.setLastModified(cacheFile.lastModified() + 10_000L);
        ShaderCacheManager.CacheStats cached = ShaderCacheManager.inspectForLaunch(paths);

        assertEquals(scanned, cached);
    }

    @Test
    public void firstLaunchWithoutSnapshotPerformsRealScan() throws Exception {
        ShaderCacheManager.CachePaths paths = paths("CUSTOM_GAME_NO_SNAPSHOT");
        ShaderCacheManager.prepare(paths, new EnvVars());
        File cacheFile = new File(paths.hostDxvkDirectory(), "existing.dxvk-cache");
        Files.write(cacheFile.toPath(), new byte[] {1, 2, 3});

        ShaderCacheManager.CacheStats initial = ShaderCacheManager.inspectForLaunch(paths);

        assertEquals(1, initial.files());
        assertEquals(3L, initial.bytes());
    }

    @Test
    public void staleSnapshotFallsBackToRealScan() throws Exception {
        ShaderCacheManager.CachePaths paths = paths("CUSTOM_GAME_STALE_SNAPSHOT");
        ShaderCacheManager.prepare(paths, new EnvVars());
        ShaderCacheManager.inspectAndSnapshot(paths);
        File cacheFile = new File(paths.hostDxvkDirectory(), "new.dxvk-cache");
        Files.write(cacheFile.toPath(), new byte[] {1, 2, 3});
        paths.hostDxvkDirectory().setLastModified(System.currentTimeMillis() + 10_000L);

        ShaderCacheManager.CacheStats refreshed = ShaderCacheManager.inspectForLaunch(paths);

        assertEquals(1, refreshed.files());
        assertEquals(3L, refreshed.bytes());
    }

    @Test
    public void corruptSnapshotFallsBackToRealScan() throws Exception {
        ShaderCacheManager.CachePaths paths = paths("CUSTOM_GAME_CORRUPT_SNAPSHOT");
        ShaderCacheManager.prepare(paths, new EnvVars());
        File cacheFile = new File(paths.hostMesaDirectory(), "mesa.cache");
        Files.write(cacheFile.toPath(), new byte[] {7, 8});
        Files.write(
                ShaderCacheManager.statsSnapshotFile(paths).toPath(),
                "broken".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        ShaderCacheManager.CacheStats refreshed = ShaderCacheManager.inspectForLaunch(paths);

        assertEquals(1, refreshed.files());
        assertEquals(2L, refreshed.bytes());
    }

    private ShaderCacheManager.CachePaths paths(String id) throws Exception {
        return paths(temporaryFolder.newFolder(id));
    }

    private ShaderCacheManager.CachePaths paths(File containerRoot) {
        return paths(containerRoot, "bionic");
    }

    private ShaderCacheManager.CachePaths paths(File containerRoot, String variant) {
        return ShaderCacheManager.pathsFor(
                containerRoot,
                "wrapper-v2",
                "25.1.0",
                "version=turnip25.1.0",
                "dxvk",
                "version=2.6.1,vkd3dVersion=2.14.1",
                variant,
                "proton-9.0-arm64ec"
        );
    }

    private ShaderCacheManager.BackendGenerations backendGenerations(
            String driver,
            String dxvk,
            String vkd3d,
            String runtimeDriver
    ) {
        return ShaderCacheManager.backendGenerations(
                "wrapper-v2",
                driver,
                "version=" + driver,
                "dxvk",
                "version=" + dxvk + ",vkd3dVersion=" + vkd3d,
                "bionic",
                "proton-9.0-arm64ec",
                runtimeDriver
        );
    }
}
