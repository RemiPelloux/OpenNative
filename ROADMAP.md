# OpenNative Roadmap

## Purpose

This roadmap defines the public delivery sequence for OpenNative after `1.1.0`. It separates committed engineering objectives from exploratory ideas and ties each milestone to explicit acceptance criteria.

Dates are intentionally omitted until the preceding quality gate passes. A feature is considered delivered only when its implementation, migration, failure recovery, documentation and validation are complete.

## Release strategy

| Milestone | Objective | Status |
| --- | --- | --- |
| `1.1.x` | Maintain the current stable runtime and address critical regressions | Stable maintenance |
| `1.2.0` | User-configured provider tabs, RSS/JSON feeds, AllDebrid, transfers and verified cleanup | Released |
| `1.2.1` | Default provider tab, cover-grid catalog and verified installer cleanup | Released |
| `1.3.0` | Skidrow extract-to-Custom, Play picks the game exe, catalog covers in Custom | Released |
| `1.3.1` | ISO9660 extraction, runtime catalog refresh and upstream compatibility fixes | Released |
| `1.5.0` | Settings sharing, cockpit polish and remaining installer-session certification | Planned |
| `2.0.0` | Introduce modular runtimes and a safe OpenNative identity/data migration | Future architecture |

Detailed plans:

- [OpenNative 1.5.0](docs/ROADMAP_1.5.0.md)
- [OpenNative 2.0.0](docs/ROADMAP_2.0.0.md)
- [Custom provider tabs and Installer Manager](docs/CUSTOM_PROVIDER_TABS.md)

## Engineering principles

1. **Preserve user data.** Existing containers, saves and local settings take priority over cosmetic migrations.
2. **Measure before optimizing.** Renderer, shader and translation changes require repeatable evidence.
3. **Fail recoverably.** Downloads, installations, imports and migrations must retain a clear recovery path.
4. **Keep secrets local.** Credentials never enter ordinary entities, logs, diagnostics or shared profiles.
5. **Protect generic ARM64 behavior.** Device tuning remains capability-based, optional and reversible.
6. **Separate metadata from execution.** Remote feeds cannot silently become filesystem paths, commands or executable policy.
7. **Document limitations.** Release notes state what is supported, experimental and unresolved.

## OpenNative 1.5.0

### Product outcomes

- Users can create provider tabs through a `+` immediately after **Custom**.
- Provider metadata remains usable offline through validated cached snapshots.
- Optional AllDebrid resolution works without exposing the user's API key.
- Transfers survive interruption, cancellation, low-space failures and process recreation.
- Portable archives and Windows installers can be installed through a reviewed, recoverable workflow.
- Installers are removed only after installation and launch metadata are verified.
- Game/global settings can be shared without paths, secrets, saves or protected content.
- The secondary-screen cockpit is consistent with OpenNative branding and reliable with touch/controller input.
- Performance work produces measurable frame-pacing, memory, thermal or responsiveness improvements.

### Delivery phases

| Phase | Scope | Required gate |
| --- | --- | --- |
| 0. Foundations | Schemas, migrations, secret store, URL policy, typed errors and fake servers | Malformed input fails closed; no secret reaches logs/entities/exports |
| 1. Provider library | Dynamic tabs, read-only feeds, paging, cache, search and ordering | Built-in sources unchanged; 10,000-item fixture remains responsive |
| 2. Resolution and transfer | AllDebrid adapter, foreground jobs, resume, retry and storage reservation | Contract, 429, redirect, timeout, cancellation and low-space tests pass |
| 3. Portable installation | Archive inspection, staging, verification and atomic promotion | No path escape; failed promotion preserves prior destination |
| 4. Installer Manager | Wine `.exe`/`.msi` sessions, process-tree tracking and executable discovery | Parent/child, timeout, reboot, cancel and missing-executable cases recover |
| 5. Cleanup and sharing | Receipts, verified deletion, recovery history and portable settings | No unverified deletion; import/export round trips and rollback pass |
| 6. Polish and certification | Cockpit, accessibility, performance audit, CI and device validation | Release acceptance matrix passes with sanitized evidence |

Phases are promoted in order. UI prototypes may happen earlier, but transfer or execution capabilities do not ship before their storage, secret and recovery foundations.

### Performance workstreams

#### Frame delivery and shaders

- Profile SurfaceFlinger compatibility conversion, Vulkan presentation, queue depth and buffer retirement.
- Remove steady-state frame-path allocations and redundant conversion only after ownership is proven.
- Correlate DXVK/Mesa/VKD3D compilation with Unreal streaming and translation-runtime stalls.
- Preserve backend-specific cache generations and perform maintenance only outside active sessions.

#### Runtime and memory

- Eliminate verified N+1 paths with query-count regression budgets.
- Bound downloads, hashing and copies with streaming buffers rather than whole-file retention.
- Deduplicate in-flight metadata, artwork, profile and component requests.
- Pause nonessential catalog, artwork and maintenance work during foreground game/install sessions.

#### Compose and interaction

- Keep network, database, hashing and file work outside recomposition.
- Use stable keyed models so job progress updates only the affected row.
- Rate-limit UI progress independently from transfer throughput.
- Preserve controller focus through dialogs, rotation, display hot-plug and activity recreation.

### Acceptance criteria

`1.5.0` is release-ready only when all of the following are true:

- Modern JVM, migration, provider-contract, transfer and installer suites pass in CI.
- No open critical or high-severity data-loss, secret-leak, rendering or input regression remains.
- Existing OpenNative data survives an in-place upgrade and rollback rehearsal.
- Provider tabs cannot modify built-in store/custom source behavior.
- AllDebrid tests use a fake server; releases and CI require no real user credential.
- Interrupted jobs resume or fail with retained recovery material.
- Cleanup cannot remove an installer before verified install-receipt commit.
- Thirty launch/stop cycles complete without ANR, native crash or unbounded RSS growth.
- A 60-minute AYN Thor session completes with recorded FPS, p95/p99, RSS, swap and temperature.
- Performance claims meet the [measurement criteria](docs/PERFORMANCE.md).

## OpenNative 2.0.0

The `2.0.0` milestone begins only after `1.5.0` certification. Its scope includes:

- A resumable migration to a final OpenNative application identity and storage layout.
- Modular Wine/Proton, Box64/FEX, graphics-driver and DirectX-wrapper contracts.
- Signed component manifests with independent update and rollback.
- A versioned OpenNative compatibility/profile catalog with local override protection.
- Explainable local optimization recommendations with confidence and rollback.
- Broader validation across Qualcomm/Adreno, Mali and additional Android ARM64 platforms.
- Unified phone, tablet, handheld and secondary-display navigation architecture.

See [docs/ROADMAP_2.0.0.md](docs/ROADMAP_2.0.0.md) for the complete architecture plan.

## Explicit non-goals

- Downloaded third-party shader caches.
- Built-in copyrighted-content discovery or indexing.
- DRM bypass or silent execution of provider downloads.
- Forced device clocks, fan firmware, unsafe math flags or global Android changes.
- Device-specific tuning enabled without consent.
- Package/storage renaming without atomic migration and rollback.
- Performance claims without reproducible before/after evidence.

## Tracking and governance

- Released behavior belongs in [CHANGELOG.md](CHANGELOG.md), not in future roadmaps.
- Performance work follows [docs/PERFORMANCE.md](docs/PERFORMANCE.md).
- Security, attribution and redistribution constraints are documented in [docs/INDEPENDENCE.md](docs/INDEPENDENCE.md) and [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES).
- Scope changes must update the detailed milestone document and its acceptance criteria before implementation is promoted.
