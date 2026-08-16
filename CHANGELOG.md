# Changelog

## 1.0.0 - 2026-08-16

- Add a bounded predictive shader warmup based only on files observed after the previous clean session.
- Reject stale, modified and path-escaping warmup entries; never download or import foreign shader caches.
- Scale shader read-ahead from 0 to 16 MiB using Android's current available-memory and low-memory state.
- Keep adaptive metrics in memory while disabling JSONL writes and periodic metric logs during normal play; persistent capture now requires diagnostics mode.
- Move the X server startup log out of the Compose recomposition path.
- Serialize shader snapshot, cleanup and health-state transitions at session boundaries without adding locks to frame delivery.
- Retain the release-candidate database batching, component integrity, shader snapshots and sustained memory-pressure governor.
- Add regression tests for warmup bounds, memory gating, changed files and managed-root confinement.

OpenNative 1.0.0 does not claim a universal FPS increase. Renderer, translation-runtime and game-specific gains still require controlled A/B captures on the same device, scene, driver and thermal state.

## 1.0.0-rc.1 - 2026-08-16

- Remove confirmed Steam, package, DLC, branch and Nexus collection N+1 query paths with chunked reads that respect SQLite's 999-parameter limit.
- Replace forced per-launch DXVK, graphics-driver and PulseAudio extraction with versioned integrity manifests and automatic repair of missing or empty critical files.
- Promote bundled components through a staged directory so a failed extraction does not destroy the previous working PulseAudio installation.
- Reuse a clean-session shader-cache statistics snapshot on the next launch, consume it before the guest starts and fall back to a real scan after crashes or cache changes.
- Add low-allocation swap and Linux PSI sampling on the existing slow resource cadence.
- Gate adaptive model training and shader maintenance with sustained memory-pressure hysteresis instead of reacting to one noisy sample.
- Share daemon process-output executors and disable unconditional guest stdout logging in release builds.
- Add focused tests for component repair, shader snapshot validation, SQLite batches above 999 IDs, swap/PSI parsing and memory-pressure transitions.

This is a release candidate. Performance uplift is not claimed until controlled A/B captures, 30 launch/stop cycles and the 60-minute AYN Thor soak pass.

## 0.3.0-beta.1 - 2026-08-16

- Add the guarded Adaptive Engine with observation-only mode as the default and explicit per-game opt-in for automatic resolution changes.
- Preserve the native resolution ceiling, apply staged changes only on the next launch and roll back unsuccessful probes.
- Classify sustained GPU, CPU, memory, thermal and frame-pacing pressure from session telemetry.
- Predict five-second p95 frametime and thermal trends with a bounded constant-state online model.
- Never lower resolution for CPU, memory or frame-pacing stalls and never change device clocks.
- Add Qualcomm/Adreno capability reporting, memory-pressure policy and Shader Health cache diagnostics.
- Add Adaptive Engine controls to the quick menu and AYN Thor secondary-screen cockpit.
- Export sanitized local diagnostics with paths and credentials redacted.
- Sample Android memory pressure on the existing slow resource cadence rather than in the frame path.
- Document the model, safeguards and remaining AYN Thor performance-certification work.

## 0.2.2 - 2026-08-16

- Add persistent per-container shader caches for DXVK, Mesa/Zink and VKD3D across Bionic and glibc launchers.
- Scope Mesa, DXVK and VKD3D generations independently so a driver update does not discard reusable DXVK state.
- Migrate the original unified cache layout by same-filesystem rename and report cold/warm cache growth for every session.
- Preserve explicit user cache paths and fall back safely if cache directories cannot be created.
- Add on-demand cache statistics and guarded cache-cleanup APIs without scanning during gameplay.

## 0.2.1 - 2026-08-16

- Fix the DXVK state cache path to use OpenNative's real application data directory.
- Apply a conservative automatic guest graphics-memory budget on 6-14 GB Android devices when a profile is still configured as unlimited.
- Reclaim SurfaceFlinger conversion buffers after transient game windows close.
- Return safely to the library when a stale launch route references a missing container.
- Document the Gamma Emerald battle-load findings and memory behavior on the 12 GB AYN Thor.

## 0.2.0 - 2026-08-16

- Make power-control affinity opt-in and respect native and WoW64 container CPU masks.
- Batch mod-profile database work and suppress duplicate custom-game icon extraction.
- Add measured Gamma Emerald CPU, thermal, memory and frame-pacing guidance.

## 0.1.0 - 2026-08-16

- Rebrand the user-facing application and documentation as OpenNative.
- Add asynchronous SurfaceFlinger compatibility conversion with safe buffer lifetime and shutdown draining.
- Add a dual-screen performance cockpit with session shortcuts and fallback behavior.
- Reduce hidden telemetry sampling and buffer JSONL session writes.
- Add portable, versioned container profile import/export.
- Batch GOG database reads and storage-path migrations to remove N+1 work.
- Provision only configured guest controller slots and gate evshim diagnostics behind `EVSHIM_DEBUG`.
- Preserve the existing application ID and storage directory for in-place test-device upgrades.
- Disable the upstream self-updater for fork builds.
