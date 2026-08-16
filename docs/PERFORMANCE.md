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

1. **Guest memory pressure:** unlimited DXVK and wrapper budgets let the session grow into heavy swap. The first controlled comparison is a 4096 MB device-memory budget with the existing 2048 MB Wine video-memory value.
2. **CPU translation and thermals:** low average GPU utilization rules out simple GPU saturation. Profile FEX/Unreal worker activity and shader compilation while tracking throttling; do not force clocks as a substitute.
3. **Conflicting affinity ownership:** the power profile previously ignored the WoW64 mask and could suppress the container mask even with game pinning disabled. OpenNative now treats affinity as opt-in and considers both masks.
4. **Surface compatibility conversion:** `sfCompatMode` still requires BGRA-to-RGBA work per frame. Submission is asynchronous now, but native profiling must quantify the remaining conversion, JNI-reference and buffer-pool cost before changing it.
5. **Repeated background work:** mod profile synchronization performed per-row reads/writes, and repeated library scans launched duplicate icon extraction jobs. These paths are now batched and deduplicated so UI activity does not compete with a running game.

Shader work remains a separate A/B target: distinguish first-use DXVK pipeline creation from Unreal asset streaming and FEX translation. A warm-cache gain must not hide cold-cache stalls or rendering errors.

## Acceptance

Promote a change when it produces at least a 3% repeatable throughput gain, a clear p95/p99 improvement, or a meaningful power/temperature reduction without more than 2% regression elsewhere. Stability, correct rendering and controller input are mandatory.
