# OpenNative 1.0.0

OpenNative 1.0.0 consolidates the fork's work since 0.1.0 into the first stable-named source and Android
ARM64 release. It focuses on lower background overhead, safer persistent caches, controller reliability,
portable profiles and performance diagnostics for handhelds such as the AYN Thor.

## New features since 0.1.0

- Dual-screen performance cockpit with a right-panel fallback on single-display devices.
- Portable, versioned container profile import and export without device-local paths.
- Adaptive Engine observation and opt-in staged resolution control with confidence, cooldown and rollback.
- Backend-aware Mesa, DXVK and VKD3D caches separated by driver, wrapper and runtime fingerprints.
- Shader Health diagnostics, safe post-exit pruning and clean-session cache snapshots.
- Predictive local shader warmup using validated recent-file manifests and memory-aware 0-16 MiB limits.
- Sanitized diagnostic exports covering performance, memory, prediction and shader state.

## Performance and stability

- SurfaceFlinger compatibility conversion no longer waits on a per-frame future; saturated work drops
  stale frames and reuses bounded buffers.
- Normal sessions no longer write JSONL performance samples or periodic metric logs. Diagnostic launches
  retain persistent capture for controlled measurements.
- Runtime components use versioned integrity manifests instead of forced extraction on every launch.
- PulseAudio promotion is staged so a failed extraction does not destroy the previous working runtime.
- Confirmed Steam, package, DLC, branch, GOG, Nexus, storage and frontend N+1 paths use chunked reads or
  grouped writes, including batches larger than SQLite's 999-parameter limit.
- Swap and Linux PSI sampling use low-allocation readers and sustained hysteresis before adaptive work is
  restricted.
- Controller routing handles D-pad hats and provisions only configured guest controller slots.
- Shader warmup rejects modified files, incompatible generations and paths outside the managed cache.
- Shader snapshot, cleanup and health-state transitions are serialized at session boundaries to avoid
  teardown races without adding locks to frame delivery.

## Gamma Emerald

- Includes a documented 1280x720, 30 FPS compatibility baseline for the AYN Thor.
- Preserves the working controller route and optional low-cost shadow profile.
- Adds persistent shader caches and conservative guest graphics-memory budgeting for Unreal workloads.

## Verification

- Focused unit coverage includes shader manifests and warmup policy, component repair, database batching,
  memory-pressure parsing/governance, Adaptive Engine behavior and controller routing.
- The Android ARM64 release build is installed as an in-place update so containers and saves remain intact.
- Performance changes are not advertised as universal FPS gains without repeatable same-scene A/B data.

## Known limitations

- Compatibility remains game, Wine/Proton, translation-runtime and GPU-driver specific.
- Downloading foreign shader caches is intentionally unsupported; OpenNative only warms caches generated
  locally for the matching container fingerprint.
- Thirty launch/stop cycles and the 60-minute AYN Thor soak remain recommended before treating a specific
  game/profile combination as certified.
- Three inherited prebuilt shims are marked proprietary and lack a confirmed downstream redistribution
  grant. Review `THIRD_PARTY_NOTICES` before redistributing the APK.

Report reproducible issues at <https://github.com/RemiPelloux/OpenNative/issues> with private paths,
credentials, saves and game files removed.
