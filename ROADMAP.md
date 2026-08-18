# OpenNative Roadmap

OpenNative `1.1.0` is the current stable line. The next milestones prioritize deterministic quality, measured runtime improvements and an architecture that can evolve without risking existing containers or saves.

## Milestones

| Milestone | Purpose | Plan |
| --- | --- | --- |
| `1.5.0` | Stabilize the current runtime and complete the handheld experience | [Detailed roadmap](docs/ROADMAP_1.5.0.md) |
| `2.0.0` | Introduce versioned runtime modules and a safe identity/data migration | [Detailed roadmap](docs/ROADMAP_2.0.0.md) |

## Current priorities

1. Keep the JVM suite deterministic across supported JDKs and prevent test infrastructure failures from hiding product regressions.
2. Profile frame delivery, shader compilation and translation-runtime stalls before changing synchronization or renderer ownership.
3. Complete the OpenNative secondary-screen cockpit with reliable controller focus, hot-plug and rotation behavior.
4. Make component downloads independently verifiable with immutable metadata, checksums and redistribution review.
5. Certify releases with repeated game captures, launch/stop cycles, soak tests and migration checks.

## Non-goals

- No downloaded third-party shader caches.
- No forced clocks, fan firmware, unsafe math flags or global Android changes.
- No device-specific behavior enabled silently.
- No performance claim without repeatable A/B evidence.
- No package or storage rename without an atomic, tested rollback path.

Released work is recorded in [CHANGELOG.md](CHANGELOG.md). The measurement protocol and promotion gates are defined in [docs/PERFORMANCE.md](docs/PERFORMANCE.md).
