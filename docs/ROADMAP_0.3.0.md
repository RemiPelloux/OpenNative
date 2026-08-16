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

Status: implemented for safe between-launch changes.

- Add an aspect-preserving resolution ladder aligned to runtime buffer requirements.
- Treat the current generic XServer path as restart-only; it has no verified XRandR resize path.
- Stage changes atomically in the per-container profile and apply before XServer construction.
- Preserve the previous resolution and baseline for rollback.
- Keep FSR EASU/RCAS as output reconstruction; compositor-only downscaling is not counted as a
  performance optimization because the guest has already rendered the frame.

## Alpha 3: model identification

Status: implemented with training gates; target-device coefficient validation remains required.

- Measure the response to one controlled resolution step in a stable GPU-bound window.
- Fit the per-title frame-cost model `T(s) = Tcpu + Tpresent + a * s^p` using bounded recursive
  least squares.
- Reject model updates during shader compilation, asset streaming, pause, focus loss or thermal
  throttling transitions.
- Keep session coefficients in memory until cross-session stability is proven. No untrusted model file is loaded.

## Beta 1: constrained controller

Status: implemented; automatic mode remains explicit opt-in.

- Evaluate discrete resolution candidates with penalties for missed deadlines, quality loss,
  temperature, memory pressure, uncertainty and visible switching.
- Require confidence, consecutive evidence, hysteresis and cooldown before applying a step.
- Restore the previous state after degradation or failed swapchain reconstruction.
- Make Observe mode the safe default for migrated installations and Fixed the immediate opt-out.

## Beta 2: Snapdragon backend

Status: capability discovery and diagnostics implemented; performance behavior remains evidence-gated.

- Discover Qualcomm/Adreno capabilities and CPU topology from runtime data, never model-name tables.
- Evaluate Android Performance Hint sessions for translation, presentation and compilation threads.
- Benchmark direct AHardwareBuffer presentation against BGRA-to-RGBA compute/blit conversion.
- Gate Adreno Vulkan features and any ARMv9/ThinLTO build by controlled A/B evidence.
- Keep non-Snapdragon behavior identical when the backend is unavailable.

## Beta 3: Shader Health and cockpit

Status: implemented.

- Show Adaptive Engine mode, decision, confidence and current/pending resolution in the quick menu.
- Show bottleneck, confidence, resolution and shader warmth in the secondary-display cockpit.
- Report cold/warm cache state and per-session growth without scanning cache trees in the frame loop.
- Queue explicit inactive-generation maintenance for after process termination; active generations are protected.
- Export a sanitized diagnostic report that removes credentials and local paths.

## Remaining device certification

- Capture alternating fixed versus adaptive runs on Gamma Emerald from the same save and scene.
- Instrument direct AHardwareBuffer presentation versus conversion; do not promote either from source inspection.
- Evaluate Performance Hint sessions only after identifying stable native thread IDs and measuring overhead.
- Complete the 60-minute soak and 30 launch/stop cycles before tagging `0.3.0` final.

## Release gates

- Five alternating A/B runs after warmup on the same save, scene, driver and fan state.
- At least 15% lower p95/p99 in a verified GPU-bound scene, or a meaningful thermal reduction.
- No regression above 2% in CPU-bound scenes.
- No frame-path filesystem work and no new per-frame managed allocation.
- Fewer than two automatic resolution changes per minute during a stable scene.
- Sixty-minute soak plus thirty launch/stop cycles without crash, ANR or abnormal memory growth.
- Generic ARM64 build and saved game/container data remain compatible.
