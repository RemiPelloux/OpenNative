# Changelog

## 0.3.0-alpha.1 - Unreleased

- Start Adaptive Engine in observation-only mode; it cannot change resolution or device clocks.
- Classify sustained GPU, CPU, memory, thermal and frame-pacing pressure from session telemetry.
- Predict five-second p95 frametime and thermal trends with a bounded constant-state online model.
- Emit confidence and resolution advice into local performance reports for controlled validation.
- Sample Android memory pressure on the existing slow resource cadence rather than in the frame path.

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
