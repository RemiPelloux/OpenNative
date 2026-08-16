# OpenNative 0.3.0 Roadmap

OpenNative 0.3.0 introduces Adaptive Engine: one session coordinator for performance observation,
resolution decisions and safe rollback. It must not change Android, force clocks or silently alter a
game profile.

## Alpha 1: observation

- Status: implemented on `release/0.3.0`.
- Classify GPU, CPU, memory, thermal and frame-pacing pressure every metrics cycle.
- Predict p95 frametime and temperature five seconds ahead with bounded online level/trend filters.
- Record confidence and `HOLD`, `LOWER` or `RAISE` advice without applying it.
- Never recommend a resolution change without an explicit FPS target.
- Never reduce resolution for CPU or memory pressure.

## Alpha 2: resolution capability layer

- Add an aspect-preserving resolution ladder aligned to runtime buffer requirements.
- Probe guest XRandR and swapchain resize support without persisting a change.
- Separate runtime-capable games from restart-only games.
- Add manual transitions and rollback before enabling automatic decisions.
- Keep FSR EASU/RCAS as output reconstruction; compositor-only downscaling is not counted as a
  performance optimization because the guest has already rendered the frame.

## Alpha 3: model identification

- Measure the response to one controlled resolution step in a stable GPU-bound window.
- Fit the per-title frame-cost model `T(s) = Tcpu + Tpresent + a * s^p` using bounded recursive
  least squares.
- Reject model updates during shader compilation, asset streaming, pause, focus loss or thermal
  throttling transitions.
- Persist only sanitized coefficients keyed by title and graphics-stack generation.

## Beta 1: constrained controller

- Evaluate discrete resolution candidates with penalties for missed deadlines, quality loss,
  temperature, memory pressure, uncertainty and visible switching.
- Require confidence, consecutive evidence, hysteresis and cooldown before applying a step.
- Restore the previous state after degradation or failed swapchain reconstruction.
- Make Fixed mode the immediate opt-out and default for migrated installations.

## Beta 2: Snapdragon backend

- Discover Qualcomm/Adreno capabilities and CPU topology from runtime data, never model-name tables.
- Evaluate Android Performance Hint sessions for translation, presentation and compilation threads.
- Benchmark direct AHardwareBuffer presentation against BGRA-to-RGBA compute/blit conversion.
- Gate Adreno Vulkan features and any ARMv9/ThinLTO build by controlled A/B evidence.
- Keep non-Snapdragon behavior identical when the backend is unavailable.

## Release gates

- Five alternating A/B runs after warmup on the same save, scene, driver and fan state.
- At least 15% lower p95/p99 in a verified GPU-bound scene, or a meaningful thermal reduction.
- No regression above 2% in CPU-bound scenes.
- No frame-path filesystem work and no new per-frame managed allocation.
- Fewer than two automatic resolution changes per minute during a stable scene.
- Sixty-minute soak plus thirty launch/stop cycles without crash, ANR or abnormal memory growth.
- Generic ARM64 build and saved game/container data remain compatible.
