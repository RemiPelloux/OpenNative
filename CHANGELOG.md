# Changelog

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

