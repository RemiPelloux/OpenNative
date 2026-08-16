# OpenNative Performance Method

Optimization work must start with a measured bottleneck. A faster microbenchmark is not enough if frame pacing, stability or thermals regress in a real game.

## Capture protocol

1. Fix the app commit, game/version, save, scene, driver, Wine/FEX/Box64 versions, resolution and frame cap.
2. Start from the same fan mode and comparable battery/temperature state.
3. Run once to warm caches, then capture five identical runs per variant.
4. Record median FPS, p95/p99 frametime, emulation speed, RSS, CPU/GPU load, shader events and temperature.
5. Alternate A/B variants where possible to reduce thermal drift.
6. Keep raw reports local if they contain paths or game metadata; publish only sanitized summaries.

## Current high-value targets

1. **Frame conversion and presentation:** confirm whether the compatibility blit is still copy-bound after asynchronous submission and quantify queue latency.
2. **Shader compilation:** separate DXVK pipeline compilation stalls from Unreal asset streaming and Wine translation overhead.
3. **Guest runtime diagnostics:** measure Box64/FEX path-rewrite and missing-opcode logging before disabling only proven production noise.
4. **Database access:** track query counts for library refresh, synchronization and storage migration to prevent N+1 regressions.
5. **Thermal sustainability:** optimize work per frame and wakeups rather than forcing clocks or affinity.

## Acceptance

Promote a change when it produces at least a 3% repeatable throughput gain, a clear p95/p99 improvement, or a meaningful power/temperature reduction without more than 2% regression elsewhere. Stability, correct rendering and controller input are mandatory.

