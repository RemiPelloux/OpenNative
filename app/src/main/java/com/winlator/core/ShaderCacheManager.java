package com.winlator.core;

import com.winlator.container.Container;
import com.winlator.core.envvars.EnvVars;
import com.winlator.xenvironment.ImageFs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Owns persistent, compatibility-scoped shader caches for one game container. */
public final class ShaderCacheManager {
    static final String CACHE_ROOT_NAME = "opennative-shaders";
    private static final String ACTIVE_GENERATION_FILE = ".active-generation";
    private static final String CACHE_LAYOUT_VERSION = "v2";
    private static final String BACKENDS_DIRECTORY = "backends";
    private static final String MESA_CACHE_VARIABLE = "MESA_SHADER_CACHE_DIR";
    private static final String DXVK_CACHE_VARIABLE = "DXVK_STATE_CACHE_PATH";
    private static final String VKD3D_CACHE_VARIABLE = "VKD3D_SHADER_CACHE_PATH";
    private static final String STATS_SNAPSHOT_PREFIX = ".stats-";
    private static final String STATS_SNAPSHOT_VERSION = "v1";
    private static final String WARMUP_MANIFEST_PREFIX = ".warmup-";
    private static final String WARMUP_MANIFEST_VERSION = "v1";
    private static final int MAX_MANIFEST_FILES = 64;
    private static final long MAX_WARMUP_MANIFEST_BYTES = 64L * 1024L;
    private static final int WARMUP_BUFFER_BYTES = 256 * 1024;

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

    /** Reuses the last clean-session snapshot when none of the active cache roots changed. */
    public static synchronized CacheStats inspectForLaunch(CachePaths paths) {
        CacheStats cached = readStatsSnapshot(paths);
        // Consume the clean-session marker. A crash cannot leave trusted stale statistics behind.
        statsSnapshotFile(paths).delete();
        return cached != null ? cached : inspect(paths);
    }

    /** Performs a real scan and atomically records it for the next launch. */
    public static synchronized CacheStats inspectAndSnapshot(CachePaths paths) {
        CacheInspection inspection = inspectWithFiles(paths);
        writeStatsSnapshot(paths, inspection.stats);
        writeWarmupManifest(paths, inspection.files);
        return inspection.stats;
    }

    /**
     * Builds a bounded read-ahead plan from files observed after the previous clean session.
     * The manifest contains only local relative paths and is rejected after any generation or
     * file metadata change. No cache content is copied, downloaded or parsed by OpenNative.
     */
    public static WarmupPlan planWarmup(CachePaths paths, long maximumBytes, int maximumFiles) {
        if (maximumBytes <= 0L || maximumFiles <= 0) return WarmupPlan.empty();
        File manifest = warmupManifestFile(paths);
        if (!manifest.isFile() || manifest.length() <= 0L
                || manifest.length() > MAX_WARMUP_MANIFEST_BYTES) return WarmupPlan.empty();
        String value = FileUtils.readString(manifest);
        if (value == null) return WarmupPlan.empty();

        try {
            String[] lines = value.trim().split("\\R");
            if (lines.length < 5 || !WARMUP_MANIFEST_VERSION.equals(lines[0])) return WarmupPlan.empty();
            if (!paths.generation.equals(markerValue(lines[1], "generation"))) return WarmupPlan.empty();
            if (Long.parseLong(markerValue(lines[2], "mesaStamp")) != rootStamp(paths.hostMesaDirectory)
                    || Long.parseLong(markerValue(lines[3], "dxvkStamp")) != rootStamp(paths.hostDxvkDirectory)
                    || Long.parseLong(markerValue(lines[4], "vkd3dStamp")) != rootStamp(paths.hostVkd3dDirectory)) {
                return WarmupPlan.empty();
            }

            File canonicalRoot = paths.hostRoot.getCanonicalFile();
            List<WarmupEntry> entries = new ArrayList<>();
            long plannedBytes = 0L;
            int fileLimit = Math.min(maximumFiles, MAX_MANIFEST_FILES);
            for (int index = 5; index < lines.length && entries.size() < fileLimit; index++) {
                String[] fields = lines[index].split("\\t", -1);
                if (fields.length != 4 || !"file".equals(fields[0])) continue;
                String relative = new String(Base64.getUrlDecoder().decode(fields[1]), StandardCharsets.UTF_8);
                long recordedLength = Long.parseLong(fields[2]);
                long recordedModified = Long.parseLong(fields[3]);
                File candidate = new File(canonicalRoot, relative).getCanonicalFile();
                if (!candidate.toPath().startsWith(canonicalRoot.toPath()) || !candidate.isFile()) continue;
                if (candidate.length() != recordedLength || candidate.lastModified() != recordedModified) continue;
                long advisedBytes = Math.min(recordedLength, maximumBytes - plannedBytes);
                if (advisedBytes <= 0L) break;
                entries.add(new WarmupEntry(candidate, advisedBytes));
                plannedBytes += advisedBytes;
            }
            return new WarmupPlan(List.copyOf(entries), plannedBytes, true);
        } catch (IOException | IllegalArgumentException ignored) {
            return WarmupPlan.empty();
        }
    }

    /** Sequentially warms the page cache with one fixed direct buffer. */
    public static WarmupResult applyWarmup(WarmupPlan plan) {
        if (plan == null || plan.entries.isEmpty()) return new WarmupResult(0, 0L, 0);
        int advisedFiles = 0;
        int skippedFiles = 0;
        long advisedBytes = 0L;
        ByteBuffer buffer = ByteBuffer.allocateDirect(WARMUP_BUFFER_BYTES);
        for (WarmupEntry entry : plan.entries) {
            try (FileInputStream input = new FileInputStream(entry.file)) {
                long remaining = entry.bytes;
                while (remaining > 0L) {
                    buffer.clear();
                    buffer.limit((int) Math.min(buffer.capacity(), remaining));
                    int read = input.getChannel().read(buffer);
                    if (read <= 0) break;
                    remaining -= read;
                }
                advisedFiles++;
                advisedBytes += entry.bytes - remaining;
            } catch (IOException ignored) {
                skippedFiles++;
            }
        }
        return new WarmupResult(advisedFiles, advisedBytes, skippedFiles);
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

    /** Expensive tree inspection intended for an explicit maintenance screen, never a frame loop. */
    public static CacheHealth inspectHealth(Container container) {
        CachePaths active = activePaths(container);
        CacheStats activeStats = inspect(active);
        CacheStats allStats = inspectAll(container);
        return new CacheHealth(
                active.generation,
                activeStats,
                allStats,
                Math.max(0L, allStats.bytes - activeStats.bytes),
                countInactiveGenerations(container, active)
        );
    }

    /**
     * Deletes oldest inactive backend generations until both limits are met. Active generations
     * and files outside the managed cache root are never candidates.
     */
    public static synchronized CacheMaintenanceResult pruneInactive(
            Container container,
            long maximumTotalBytes,
            int maximumInactiveGenerations
    ) {
        if (maximumTotalBytes < 0 || maximumInactiveGenerations < 0) {
            throw new IllegalArgumentException("Cache maintenance limits must be non-negative");
        }
        CachePaths active = activePaths(container);
        File root = managedRoot(container);
        List<GenerationDirectory> candidates = inactiveGenerationDirectories(container, active);
        candidates.sort(Comparator.comparingLong(GenerationDirectory::newestWriteMillis));

        long totalBytes = inspectPath(root).bytes;
        long freedBytes = 0L;
        int removed = 0;
        int remaining = candidates.size();
        for (GenerationDirectory candidate : candidates) {
            if (totalBytes <= maximumTotalBytes && remaining <= maximumInactiveGenerations) break;
            if (deleteTreeInsideRoot(root, candidate.directory)) {
                totalBytes = Math.max(0L, totalBytes - candidate.bytes);
                freedBytes += candidate.bytes;
                removed++;
                remaining--;
            }
        }
        return new CacheMaintenanceResult(removed, freedBytes, totalBytes, remaining);
    }

    public static synchronized boolean clearActive(Container container) {
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
        if (cleared) {
            new File(allowedRoot, ACTIVE_GENERATION_FILE).delete();
            statsSnapshotFile(paths).delete();
        }
        return cleared;
    }

    public static synchronized boolean clearAll(Container container) {
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

    private static CacheInspection inspectWithFiles(CachePaths paths) {
        Set<File> roots = new LinkedHashSet<>();
        if (paths.splitLayout) {
            roots.add(paths.hostMesaDirectory);
            roots.add(paths.hostDxvkDirectory);
            roots.add(paths.hostVkd3dDirectory);
        } else {
            roots.add(paths.hostRoot);
        }
        Comparator<CacheFile> oldestFirst = Comparator.comparingLong(CacheFile::modified)
                .thenComparingLong(CacheFile::length);
        PriorityQueue<CacheFile> newestFiles = new PriorityQueue<>(MAX_MANIFEST_FILES, oldestFirst);
        long bytes = 0L;
        int fileCount = 0;
        long newest = 0L;
        for (File root : roots) {
            if (!root.isDirectory()) continue;
            Deque<File> pending = new ArrayDeque<>();
            pending.add(root);
            while (!pending.isEmpty()) {
                File[] children = pending.removeFirst().listFiles();
                if (children == null) continue;
                for (File child : children) {
                    if (child.isDirectory()) {
                        pending.addLast(child);
                    } else if (child.isFile()) {
                        long length = child.length();
                        long modified = child.lastModified();
                        bytes += length;
                        fileCount++;
                        newest = Math.max(newest, modified);
                        if (length > 0L) {
                            CacheFile oldest = newestFiles.peek();
                            if (newestFiles.size() < MAX_MANIFEST_FILES) {
                                newestFiles.add(new CacheFile(child, length, modified));
                            } else if (oldest != null && (modified > oldest.modified
                                    || modified == oldest.modified && length > oldest.length)) {
                                newestFiles.poll();
                                newestFiles.add(new CacheFile(child, length, modified));
                            }
                        }
                    }
                }
            }
        }
        List<CacheFile> files = new ArrayList<>(newestFiles);
        files.sort(Comparator.comparingLong(CacheFile::modified).reversed()
                .thenComparing(Comparator.comparingLong(CacheFile::length).reversed()));
        return new CacheInspection(new CacheStats(bytes, fileCount, newest), files);
    }

    private static CacheStats readStatsSnapshot(CachePaths paths) {
        File snapshot = statsSnapshotFile(paths);
        if (!snapshot.isFile() || snapshot.length() <= 0L) return null;
        String value = FileUtils.readString(snapshot);
        if (value == null) return null;
        try {
            String[] lines = value.trim().split("\\R");
            if (lines.length != 8 || !STATS_SNAPSHOT_VERSION.equals(lines[0])) return null;
            if (!paths.generation.equals(markerValue(lines[1], "generation"))) return null;
            long mesaStamp = Long.parseLong(markerValue(lines[2], "mesaStamp"));
            long dxvkStamp = Long.parseLong(markerValue(lines[3], "dxvkStamp"));
            long vkd3dStamp = Long.parseLong(markerValue(lines[4], "vkd3dStamp"));
            if (mesaStamp != rootStamp(paths.hostMesaDirectory)
                    || dxvkStamp != rootStamp(paths.hostDxvkDirectory)
                    || vkd3dStamp != rootStamp(paths.hostVkd3dDirectory)) return null;
            long bytes = Long.parseLong(markerValue(lines[5], "bytes"));
            int files = Integer.parseInt(markerValue(lines[6], "files"));
            long newest = Long.parseLong(markerValue(lines[7], "newest"));
            if (bytes < 0L || files < 0 || newest < 0L) return null;
            return new CacheStats(bytes, files, newest);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void writeStatsSnapshot(CachePaths paths, CacheStats stats) {
        File snapshot = statsSnapshotFile(paths);
        File parent = snapshot.getParentFile();
        if (parent == null || !createDirectory(parent)) return;
        File temp = new File(parent, snapshot.getName() + ".tmp");
        String value = STATS_SNAPSHOT_VERSION + "\n"
                + "generation=" + paths.generation + "\n"
                + "mesaStamp=" + rootStamp(paths.hostMesaDirectory) + "\n"
                + "dxvkStamp=" + rootStamp(paths.hostDxvkDirectory) + "\n"
                + "vkd3dStamp=" + rootStamp(paths.hostVkd3dDirectory) + "\n"
                + "bytes=" + stats.bytes + "\n"
                + "files=" + stats.files + "\n"
                + "newest=" + stats.newestWriteMillis + "\n";
        try {
            Files.write(temp.toPath(), value.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp.toPath(), snapshot.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveUnavailable) {
                Files.move(temp.toPath(), snapshot.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            temp.delete();
        }
    }

    private static void writeWarmupManifest(CachePaths paths, List<CacheFile> files) {
        File manifest = warmupManifestFile(paths);
        File parent = manifest.getParentFile();
        if (parent == null || !createDirectory(parent)) return;
        File temp = new File(parent, manifest.getName() + ".tmp");
        StringBuilder value = new StringBuilder(512)
                .append(WARMUP_MANIFEST_VERSION).append('\n')
                .append("generation=").append(paths.generation).append('\n')
                .append("mesaStamp=").append(rootStamp(paths.hostMesaDirectory)).append('\n')
                .append("dxvkStamp=").append(rootStamp(paths.hostDxvkDirectory)).append('\n')
                .append("vkd3dStamp=").append(rootStamp(paths.hostVkd3dDirectory)).append('\n');
        try {
            File canonicalRoot = paths.hostRoot.getCanonicalFile();
            for (CacheFile cacheFile : files) {
                File canonicalFile = cacheFile.file.getCanonicalFile();
                if (!canonicalFile.toPath().startsWith(canonicalRoot.toPath())) continue;
                String relative = canonicalRoot.toPath().relativize(canonicalFile.toPath()).toString();
                String encoded = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(relative.getBytes(StandardCharsets.UTF_8));
                value.append("file\t").append(encoded)
                        .append('\t').append(cacheFile.length)
                        .append('\t').append(cacheFile.modified)
                        .append('\n');
            }
            Files.write(temp.toPath(), value.toString().getBytes(StandardCharsets.UTF_8));
            replaceAtomically(temp, manifest);
        } catch (IOException ignored) {
            temp.delete();
        }
    }

    private static void replaceAtomically(File temp, File target) throws IOException {
        try {
            Files.move(temp.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveUnavailable) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static File statsSnapshotFile(CachePaths paths) {
        return new File(paths.hostRoot, STATS_SNAPSHOT_PREFIX + paths.generation);
    }

    static File warmupManifestFile(CachePaths paths) {
        return new File(paths.hostRoot, WARMUP_MANIFEST_PREFIX + paths.generation);
    }

    private static long rootStamp(File root) {
        return root.isDirectory() ? root.lastModified() : -1L;
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

    private static int countInactiveGenerations(Container container, CachePaths active) {
        return inactiveGenerationDirectories(container, active).size();
    }

    private static List<GenerationDirectory> inactiveGenerationDirectories(
            Container container,
            CachePaths active
    ) {
        List<GenerationDirectory> result = new ArrayList<>();
        File backends = new File(managedRoot(container), BACKENDS_DIRECTORY);
        File[] backendTypes = backends.listFiles(File::isDirectory);
        if (backendTypes == null) return result;
        for (File backend : backendTypes) {
            String activeGeneration = switch (backend.getName()) {
                case "mesa" -> active.backendGenerations == null ? "" : active.backendGenerations.mesa;
                case "dxvk" -> active.backendGenerations == null ? "" : active.backendGenerations.dxvk;
                case "vkd3d" -> active.backendGenerations == null ? "" : active.backendGenerations.vkd3d;
                default -> "";
            };
            File[] generations = backend.listFiles(File::isDirectory);
            if (generations == null) continue;
            for (File generation : generations) {
                if (!isGeneration(generation.getName()) || generation.getName().equals(activeGeneration)) continue;
                CacheStats stats = inspectPath(generation);
                result.add(new GenerationDirectory(generation, stats.bytes, stats.newestWriteMillis));
            }
        }
        return result;
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

    public record CacheHealth(
            String activeGeneration,
            CacheStats active,
            CacheStats total,
            long inactiveBytes,
            int inactiveGenerations
    ) {}

    public record CacheMaintenanceResult(
            int removedGenerations,
            long freedBytes,
            long remainingBytes,
            int remainingInactiveGenerations
    ) {}

    public record WarmupEntry(File file, long bytes) {}

    public record WarmupPlan(List<WarmupEntry> entries, long bytes, boolean fromManifest) {
        static WarmupPlan empty() {
            return new WarmupPlan(List.of(), 0L, false);
        }
    }

    public record WarmupResult(int advisedFiles, long advisedBytes, int skippedFiles) {}

    private record GenerationDirectory(File directory, long bytes, long newestWriteMillis) {}

    private record CacheFile(File file, long length, long modified) {}

    private record CacheInspection(CacheStats stats, List<CacheFile> files) {}
}
