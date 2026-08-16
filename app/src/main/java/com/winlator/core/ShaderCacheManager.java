package com.winlator.core;

import com.winlator.container.Container;
import com.winlator.core.envvars.EnvVars;
import com.winlator.xenvironment.ImageFs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Owns persistent, compatibility-scoped shader caches for one game container. */
public final class ShaderCacheManager {
    static final String CACHE_ROOT_NAME = "opennative-shaders";
    private static final String ACTIVE_GENERATION_FILE = ".active-generation";
    private static final String CACHE_LAYOUT_VERSION = "v2";
    private static final String BACKENDS_DIRECTORY = "backends";
    private static final String MESA_CACHE_VARIABLE = "MESA_SHADER_CACHE_DIR";
    private static final String DXVK_CACHE_VARIABLE = "DXVK_STATE_CACHE_PATH";
    private static final String VKD3D_CACHE_VARIABLE = "VKD3D_SHADER_CACHE_PATH";

    private ShaderCacheManager() {}

    public static CachePaths prepare(Container container, EnvVars envVars) {
        String runtimeDriverIdentity = runtimeDriverIdentity(envVars);
        CachePaths paths = pathsFor(container, runtimeDriverIdentity);
        migrateLegacy(container, runtimeDriverIdentity, paths);
        if (prepare(paths, envVars)) {
            FileUtils.writeString(
                    new File(managedRoot(container), ACTIVE_GENERATION_FILE),
                    activeMarker(paths)
            );
        }
        return paths;
    }

    static boolean prepare(CachePaths paths, EnvVars envVars) {
        boolean ready = createDirectory(paths.hostRoot)
                && createDirectory(paths.hostMesaDirectory)
                && createDirectory(paths.hostDxvkDirectory)
                && createDirectory(paths.hostVkd3dDirectory);

        if (ready) {
            putDefault(envVars, MESA_CACHE_VARIABLE, paths.guestMesaDirectory);
            putDefault(envVars, DXVK_CACHE_VARIABLE, paths.guestDxvkDirectory);
            putDefault(envVars, VKD3D_CACHE_VARIABLE, paths.guestVkd3dCacheFile);
        }
        return ready;
    }

    public static CachePaths pathsFor(Container container) {
        return pathsFor(container, "");
    }

    private static CachePaths pathsFor(Container container, String runtimeDriverIdentity) {
        if (container == null || container.getRootDir() == null) {
            throw new IllegalArgumentException("Container root must be available before preparing shader caches");
        }

        return pathsFor(
                container.getRootDir(),
                container.getGraphicsDriver(),
                container.getGraphicsDriverVersion(),
                container.getGraphicsDriverConfig(),
                container.getDXWrapper(),
                container.getDXWrapperConfig(),
                container.getContainerVariant(),
                container.getWineVersion(),
                runtimeDriverIdentity
        );
    }

    static CachePaths pathsFor(
            File containerRoot,
            String graphicsDriver,
            String graphicsDriverVersion,
            String graphicsDriverConfig,
            String dxWrapper,
            String dxWrapperConfig,
            String containerVariant,
            String wineVersion
    ) {
        return pathsFor(
                containerRoot,
                graphicsDriver,
                graphicsDriverVersion,
                graphicsDriverConfig,
                dxWrapper,
                dxWrapperConfig,
                containerVariant,
                wineVersion,
                ""
        );
    }

    static CachePaths pathsFor(
            File containerRoot,
            String graphicsDriver,
            String graphicsDriverVersion,
            String graphicsDriverConfig,
            String dxWrapper,
            String dxWrapperConfig,
            String containerVariant,
            String wineVersion,
            String runtimeDriverIdentity
    ) {
        BackendGenerations generations = backendGenerations(
                graphicsDriver,
                graphicsDriverVersion,
                graphicsDriverConfig,
                dxWrapper,
                dxWrapperConfig,
                containerVariant,
                wineVersion,
                runtimeDriverIdentity
        );
        return pathsForGenerations(containerRoot, containerVariant, generations);
    }

    static BackendGenerations backendGenerations(
            String graphicsDriver,
            String graphicsDriverVersion,
            String graphicsDriverConfig,
            String dxWrapper,
            String dxWrapperConfig,
            String containerVariant,
            String wineVersion,
            String runtimeDriverIdentity
    ) {
        KeyValueSet graphics = new KeyValueSet(graphicsDriverConfig);
        KeyValueSet wrapper = new KeyValueSet(dxWrapperConfig);
        String mesa = shortHash(String.join("\n",
                "mesa-v1",
                normalized(graphicsDriver),
                normalized(graphicsDriverVersion),
                normalized(graphics.get("version")),
                normalized(graphics.get("adrenotoolsDriver")),
                normalized(containerVariant),
                normalized(runtimeDriverIdentity)
        ));
        String dxvk = shortHash(String.join("\n",
                "dxvk-v1",
                normalized(dxWrapper),
                normalized(wrapper.get("version")),
                normalized(containerVariant)
        ));
        String vkd3d = shortHash(String.join("\n",
                "vkd3d-v1",
                normalized(wrapper.get("vkd3dVersion")),
                normalized(containerVariant),
                normalized(wineVersion),
                normalized(runtimeDriverIdentity)
        ));
        return new BackendGenerations(mesa, dxvk, vkd3d);
    }

    private static CachePaths pathsForGenerations(
            File containerRoot,
            String containerVariant,
            BackendGenerations generations
    ) {
        File cacheRoot = new File(new File(containerRoot, ".cache"), CACHE_ROOT_NAME);
        File backendRoot = new File(cacheRoot, BACKENDS_DIRECTORY);
        File hostMesaDirectory = new File(new File(backendRoot, "mesa"), generations.mesa);
        File hostDxvkDirectory = new File(new File(backendRoot, "dxvk"), generations.dxvk);
        File hostVkd3dDirectory = new File(new File(backendRoot, "vkd3d"), generations.vkd3d);
        String runtimeRoot = Container.GLIBC.equalsIgnoreCase(containerVariant)
                ? ImageFs.CACHE_PATH + "/" + CACHE_ROOT_NAME + "/" + BACKENDS_DIRECTORY
                : backendRoot.getAbsolutePath();
        return new CachePaths(
                shortHash(generations.mesa + "\n" + generations.dxvk + "\n" + generations.vkd3d),
                generations,
                true,
                cacheRoot,
                hostMesaDirectory,
                hostDxvkDirectory,
                hostVkd3dDirectory,
                runtimeRoot + "/mesa/" + generations.mesa,
                runtimeRoot + "/dxvk/" + generations.dxvk,
                runtimeRoot + "/vkd3d/" + generations.vkd3d + "/vkd3d-proton.cache"
        );
    }

    static String generationKey(
            String graphicsDriver,
            String graphicsDriverVersion,
            String graphicsDriverConfig,
            String dxWrapper,
            String dxWrapperConfig,
            String containerVariant,
            String wineVersion
    ) {
        return generationKey(
                graphicsDriver,
                graphicsDriverVersion,
                graphicsDriverConfig,
                dxWrapper,
                dxWrapperConfig,
                containerVariant,
                wineVersion,
                ""
        );
    }

    static String generationKey(
            String graphicsDriver,
            String graphicsDriverVersion,
            String graphicsDriverConfig,
            String dxWrapper,
            String dxWrapperConfig,
            String containerVariant,
            String wineVersion,
            String runtimeDriverIdentity
    ) {
        KeyValueSet graphics = new KeyValueSet(graphicsDriverConfig);
        KeyValueSet wrapper = new KeyValueSet(dxWrapperConfig);
        String identity = String.join("\n",
                normalized(graphicsDriver),
                normalized(graphicsDriverVersion),
                normalized(graphics.get("version")),
                normalized(graphics.get("adrenotoolsDriver")),
                normalized(dxWrapper),
                normalized(wrapper.get("version")),
                normalized(wrapper.get("vkd3dVersion")),
                normalized(containerVariant),
                normalized(wineVersion),
                normalized(runtimeDriverIdentity)
        );
        return sha256(identity).substring(0, 16);
    }

    public static CacheStats inspectActive(Container container) {
        return inspect(activePaths(container));
    }

    public static CacheStats inspect(CachePaths paths) {
        if (!paths.splitLayout) return inspectPath(paths.hostRoot);
        Set<File> roots = new LinkedHashSet<>();
        roots.add(paths.hostMesaDirectory);
        roots.add(paths.hostDxvkDirectory);
        roots.add(paths.hostVkd3dDirectory);
        return inspectPaths(roots);
    }

    public static CacheSessionResult compare(CacheStats before, CacheStats after) {
        return new CacheSessionResult(
                before.files > 0 || before.bytes > 0,
                Math.max(0, after.files - before.files),
                Math.max(0L, after.bytes - before.bytes),
                after.newestWriteMillis > before.newestWriteMillis
        );
    }

    public static CacheStats inspectAll(Container container) {
        File root = new File(new File(container.getRootDir(), ".cache"), CACHE_ROOT_NAME);
        return inspectPath(root);
    }

    public static boolean clearActive(Container container) {
        CachePaths paths = activePaths(container);
        File allowedRoot = managedRoot(container);
        boolean cleared;
        if (paths.splitLayout) {
            cleared = deleteTreeInsideRoot(allowedRoot, paths.hostMesaDirectory)
                    & deleteTreeInsideRoot(allowedRoot, paths.hostDxvkDirectory)
                    & deleteTreeInsideRoot(allowedRoot, paths.hostVkd3dDirectory);
        } else {
            cleared = deleteTreeInsideRoot(allowedRoot, paths.hostRoot);
        }
        if (cleared) new File(allowedRoot, ACTIVE_GENERATION_FILE).delete();
        return cleared;
    }

    public static boolean clearAll(Container container) {
        File root = managedRoot(container);
        return deleteTreeInsideRoot(root, root);
    }

    static CacheStats inspectPath(File root) {
        if (!root.isDirectory()) return new CacheStats(0L, 0, 0L);
        long bytes = 0L;
        int files = 0;
        long newest = 0L;
        Deque<File> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            File[] children = pending.removeFirst().listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) {
                    pending.addLast(child);
                } else if (child.isFile()) {
                    bytes += child.length();
                    files++;
                    newest = Math.max(newest, child.lastModified());
                }
            }
        }
        return new CacheStats(bytes, files, newest);
    }

    private static CacheStats inspectPaths(Set<File> roots) {
        long bytes = 0L;
        int files = 0;
        long newest = 0L;
        for (File root : roots) {
            CacheStats stats = inspectPath(root);
            bytes += stats.bytes;
            files += stats.files;
            newest = Math.max(newest, stats.newestWriteMillis);
        }
        return new CacheStats(bytes, files, newest);
    }

    static boolean deleteTreeInsideRoot(File allowedRoot, File target) {
        try {
            allowedRoot = allowedRoot.getCanonicalFile();
            File canonicalTarget = target.getCanonicalFile();
            if (!canonicalTarget.toPath().startsWith(allowedRoot.toPath())) return false;
            return !canonicalTarget.exists() || FileUtils.delete(canonicalTarget);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static File managedRoot(Container container) {
        return new File(new File(container.getRootDir(), ".cache"), CACHE_ROOT_NAME);
    }

    private static CachePaths activePaths(Container container) {
        File marker = new File(managedRoot(container), ACTIVE_GENERATION_FILE);
        String markerValue = FileUtils.readString(marker);
        BackendGenerations generations = parseActiveMarker(markerValue);
        if (generations != null) {
            return pathsForGenerations(container.getRootDir(), container.getContainerVariant(), generations);
        }
        if (markerValue != null && markerValue.trim().matches("[0-9a-f]{16}")) {
            return legacyPathsForGeneration(
                    container.getRootDir(),
                    container.getContainerVariant(),
                    markerValue.trim()
            );
        }
        return pathsFor(container);
    }

    private static CachePaths legacyPathsForGeneration(
            File containerRoot,
            String containerVariant,
            String generation
    ) {
        File cacheRoot = new File(new File(containerRoot, ".cache"), CACHE_ROOT_NAME);
        File hostRoot = new File(cacheRoot, generation);
        String runtimeRoot = Container.GLIBC.equalsIgnoreCase(containerVariant)
                ? ImageFs.CACHE_PATH + "/" + CACHE_ROOT_NAME + "/" + generation
                : hostRoot.getAbsolutePath();
        return new CachePaths(
                generation,
                null,
                false,
                hostRoot,
                new File(hostRoot, "mesa"),
                new File(hostRoot, "dxvk"),
                new File(hostRoot, "vkd3d"),
                runtimeRoot + "/mesa",
                runtimeRoot + "/dxvk",
                runtimeRoot + "/vkd3d/vkd3d-proton.cache"
        );
    }

    private static String activeMarker(CachePaths paths) {
        return CACHE_LAYOUT_VERSION + "\n"
                + "mesa=" + paths.backendGenerations.mesa + "\n"
                + "dxvk=" + paths.backendGenerations.dxvk + "\n"
                + "vkd3d=" + paths.backendGenerations.vkd3d + "\n";
    }

    private static BackendGenerations parseActiveMarker(String marker) {
        if (marker == null) return null;
        String[] lines = marker.trim().split("\\R");
        if (lines.length != 4 || !CACHE_LAYOUT_VERSION.equals(lines[0])) return null;
        String mesa = markerValue(lines[1], "mesa");
        String dxvk = markerValue(lines[2], "dxvk");
        String vkd3d = markerValue(lines[3], "vkd3d");
        if (!isGeneration(mesa) || !isGeneration(dxvk) || !isGeneration(vkd3d)) return null;
        return new BackendGenerations(mesa, dxvk, vkd3d);
    }

    private static String markerValue(String line, String name) {
        String prefix = name + "=";
        return line.startsWith(prefix) ? line.substring(prefix.length()) : "";
    }

    private static boolean isGeneration(String value) {
        return value != null && value.matches("[0-9a-f]{16}");
    }

    private static void migrateLegacy(
            Container container,
            String runtimeDriverIdentity,
            CachePaths target
    ) {
        String legacyGeneration = generationKey(
                container.getGraphicsDriver(),
                container.getGraphicsDriverVersion(),
                container.getGraphicsDriverConfig(),
                container.getDXWrapper(),
                container.getDXWrapperConfig(),
                container.getContainerVariant(),
                container.getWineVersion(),
                runtimeDriverIdentity
        );
        migrateLegacy(container.getRootDir(), legacyGeneration, target);
    }

    static void migrateLegacy(File containerRoot, String legacyGeneration, CachePaths target) {
        File legacyRoot = new File(
                new File(new File(containerRoot, ".cache"), CACHE_ROOT_NAME),
                legacyGeneration
        );
        if (!legacyRoot.isDirectory()) return;

        moveLegacyDirectory(new File(legacyRoot, "mesa"), target.hostMesaDirectory);
        moveLegacyDirectory(new File(legacyRoot, "dxvk"), target.hostDxvkDirectory);
        moveLegacyDirectory(new File(legacyRoot, "vkd3d"), target.hostVkd3dDirectory);
        File[] remaining = legacyRoot.listFiles();
        if (remaining != null && remaining.length == 0) legacyRoot.delete();
    }

    private static void moveLegacyDirectory(File source, File target) {
        if (!source.isDirectory() || target.exists()) return;
        File parent = target.getParentFile();
        if (parent != null && createDirectory(parent)) source.renameTo(target);
    }

    private static String runtimeDriverIdentity(EnvVars envVars) {
        return String.join("\n",
                envVars.get("ADRENOTOOLS_DRIVER_PATH"),
                envVars.get("ADRENOTOOLS_DRIVER_NAME"),
                envVars.get("VK_ICD_FILENAMES"),
                envVars.get("WRAPPER_VK_VERSION"),
                envVars.get("GALLIUM_DRIVER")
        );
    }

    private static void putDefault(EnvVars envVars, String name, String value) {
        if (!envVars.has(name) || envVars.get(name).isBlank()) envVars.put(name, value);
    }

    private static boolean createDirectory(File directory) {
        return directory.isDirectory() || directory.mkdirs() || directory.isDirectory();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String shortHash(String value) {
        return sha256(value).substring(0, 16);
    }

    public record CachePaths(
            String generation,
            BackendGenerations backendGenerations,
            boolean splitLayout,
            File hostRoot,
            File hostMesaDirectory,
            File hostDxvkDirectory,
            File hostVkd3dDirectory,
            String guestMesaDirectory,
            String guestDxvkDirectory,
            String guestVkd3dCacheFile
    ) {}

    public record BackendGenerations(String mesa, String dxvk, String vkd3d) {}

    public record CacheStats(long bytes, int files, long newestWriteMillis) {}

    public record CacheSessionResult(
            boolean warmAtLaunch,
            int addedFiles,
            long addedBytes,
            boolean wroteCache
    ) {}
}
