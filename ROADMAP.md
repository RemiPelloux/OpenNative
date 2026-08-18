# OpenNative Roadmap

OpenNative `1.1.0` is the current stable line. The next milestones prioritize deterministic quality, measured runtime improvements and an architecture that can evolve without risking existing containers or saves.

## Milestones

| Milestone | Purpose | Plan |
| --- | --- | --- |
| `1.5.0` | Stabilize the current runtime and complete the handheld experience | [Detailed roadmap](docs/ROADMAP_1.5.0.md) |
| `2.0.0` | Introduce versioned runtime modules and a safe identity/data migration | [Detailed roadmap](docs/ROADMAP_2.0.0.md) |

## 1.5.0 delivery order

| Stage | Deliverable | Gate |
| --- | --- | --- |
| 0 | Schemas, secret store, URL policy and test fixtures | No secret leakage; migrations and malformed inputs covered |
| 1 | Dynamic provider tabs and read-only paginated feeds | Built-in sources unchanged; offline snapshot works |
| 2 | AllDebrid adapter and resumable foreground transfers | Fake-server contract passes; cancellation and low-space recovery pass |
| 3 | Portable archive installation and launch-candidate review | Path confinement, hash failure and atomic promotion pass |
| 4 | Wine `.exe`/`.msi` Installer Manager | Child-process, timeout, reboot and executable-discovery cases pass |
| 5 | Verified cleanup, settings sharing and recovery UX | No deletion before receipt commit; import/export round trip passes |
| 6 | Cockpit polish, performance audit and device certification | CI, 30 cycles, 60-minute soak and AYN Thor acceptance pass |

Stages are promoted in order. A later UI may be prototyped earlier, but downloads and installers do not ship before their storage, secret and recovery contracts pass.

## Current priorities

1. Keep the JVM suite deterministic across supported JDKs and prevent test infrastructure failures from hiding product regressions.
2. Profile frame delivery, shader compilation and translation-runtime stalls before changing synchronization or renderer ownership.
3. Complete the OpenNative secondary-screen cockpit with reliable controller focus, hot-plug and rotation behavior.
4. Add private-by-design exports for sharing per-game profiles and reusable settings presets with other users.
5. Add user-created provider tabs through a `+` after Custom, with optional AllDebrid resolution and safe install/cleanup workflows.
6. Make component downloads independently verifiable with immutable metadata, checksums and redistribution review.
7. Certify releases with repeated game captures, launch/stop cycles, soak tests and migration checks.

## Non-goals

- No downloaded third-party shader caches.
- No forced clocks, fan firmware, unsafe math flags or global Android changes.
- No device-specific behavior enabled silently.
- No performance claim without repeatable A/B evidence.
- No package or storage rename without an atomic, tested rollback path.
- No built-in copyrighted-content index, DRM bypass or automatic execution of provider downloads.

Released work is recorded in [CHANGELOG.md](CHANGELOG.md). The measurement protocol and promotion gates are defined in [docs/PERFORMANCE.md](docs/PERFORMANCE.md).
