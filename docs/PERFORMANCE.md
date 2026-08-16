# OpenNative Performance Method

Optimization work must start with a measured bottleneck. A faster microbenchmark is not enough if frame pacing, stability or thermals regress in a real game.

## Capture protocol

1. Fix the app commit, game/version, save, scene, driver, Wine/FEX/Box64 versions, resolution and frame cap.
2. Start from the same fan mode and comparable battery/temperature state.
3. Run once to warm caches, then capture five identical runs per variant.
4. Record median FPS, p95/p99 frametime, emulation speed, RSS, CPU/GPU load, shader events and temperature.
5. Alternate A/B variants where possible to reduce thermal drift.
6. Keep raw reports local if they contain paths or game metadata; publish only sanitized summaries.

## Gamma Emerald: measured top five

The current AYN Thor capture averaged 23.28 FPS with 68.46 ms median p95 frametime, 75.39% CPU, 31% GPU, 88 C peak CPU and 84 C peak GPU. The game used roughly 3.1-3.3 GB RSS while the device had about 600 MB available and more than 2.2 GB total swap in use.

1. **Guest memory pressure:** unlimited DXVK and wrapper budgets let the session grow into heavy swap. OpenNative now supplies a 4096 MB device-memory budget on 9-14 GB Android devices when both settings are still unlimited, while preserving every explicit user limit.
2. **CPU translation and thermals:** low average GPU utilization rules out simple GPU saturation. Profile FEX/Unreal worker activity and shader compilation while tracking throttling; do not force clocks as a substitute.
3. **Conflicting affinity ownership:** the power profile previously ignored the WoW64 mask and could suppress the container mask even with game pinning disabled. OpenNative now treats affinity as opt-in and considers both masks.
4. **Surface compatibility conversion:** `sfCompatMode` still requires BGRA-to-RGBA work per frame. Submission is asynchronous now, but native profiling must quantify the remaining conversion, JNI-reference and buffer-pool cost before changing it.
5. **Repeated background work:** mod profile synchronization performed per-row reads/writes, and repeated library scans launched duplicate icon extraction jobs. These paths are now batched and deduplicated so UI activity does not compete with a running game.

Shader work remains a separate A/B target: distinguish first-use DXVK pipeline creation from Unreal asset streaming and FEX translation. A warm-cache gain must not hide cold-cache stalls or rendering errors.

The DXVK state cache now uses OpenNative's runtime-derived app data path instead of the obsolete upstream package path. The SurfaceFlinger compatibility renderer also reclaims converted-buffer pools when transient game windows retire, preventing window churn from retaining up to eight full-size buffers per old window.

OpenNative 0.2.2 manages shader caches under each container's home directory. DXVK, Mesa/Zink and VKD3D receive independent compatibility generations below `.cache/opennative-shaders/backends`. Bionic receives the Android-host path used by its native launcher, while glibc receives the equivalent `/home/xuser` rootfs path. Mesa and VKD3D rotate when the effective Vulkan driver changes; DXVK state survives a driver update and rotates only when its own wrapper/runtime format changes. The original unified layout is migrated with same-filesystem renames, not copied during startup. Explicit user paths take precedence.

Each normal session reports whether the cache was warm at launch, whether any backend wrote new data, and file/byte growth at termination. Cache inspection runs only at launch and termination; the frame loop never walks the cache tree. Validation must compare the same scene twice after a clean generation and record first-run versus warm-run frametime p95/p99 and shader events. A warm-cache improvement is not a general FPS claim.

## Acceptance

Promote a change when it produces at least a 3% repeatable throughput gain, a clear p95/p99 improvement, or a meaningful power/temperature reduction without more than 2% regression elsewhere. Stability, correct rendering and controller input are mandatory.
