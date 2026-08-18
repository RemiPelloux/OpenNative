# OpenNative 1.5.0 Roadmap

## Goal

`1.5.0` is a stabilization and handheld-quality milestone built on the existing package and runtime architecture. It must improve confidence, frame pacing and daily usability without migrating user data or introducing unsafe device tuning.

## 1. Deterministic quality gate

- Keep Mockito and MockK instrumentation explicit and compatible with the supported JDK.
- Separate lightweight policy tests from storage-heavy integration tests and document their disk budgets.
- Bound test-worker memory and prevent concurrent Gradle builds from sharing mutable Kotlin caches.
- Fix product behavior when a current assertion finds a regression; update a test only when the public contract changed intentionally.
- Add CI gates for JVM tests, lint, release compilation, link validation and `git diff --check`.
- Publish checksums and an SBOM for release artifacts.

Exit criteria: the modern JVM suite passes twice from clean state in CI, failures retain useful reports, and generated outputs are cleaned by a documented command.

## 2. Frame delivery and shader stability

- Capture Perfetto traces around SurfaceFlinger compatibility conversion, buffer retirement, Vulkan presentation and guest frame pacing.
- Quantify remaining BGRA-to-RGBA copies, JNI references and queue saturation before changing ownership.
- Correlate DXVK pipeline creation, Mesa/VKD3D cache writes, Unreal asset streaming and translation-runtime stalls.
- Expose per-title cache generation, size, last clean shutdown and invalidation reason.
- Keep cache repair explicit, path-confined and deferred until the guest exits.

Exit criteria: each promoted change improves median throughput by at least 3%, clearly reduces p95/p99 frametime, or lowers sustained power/temperature without a regression above 2% in another representative workload.

## 3. Runtime and data efficiency

- Add query-count regression tests to remaining Room and store-library aggregation paths.
- Replace per-item reads/writes with bounded bulk operations where profiling confirms N+1 behavior.
- Cache immutable launch metadata and avoid rebuilding container state during Compose recomposition.
- Debounce profile/session persistence and keep diagnostics off the normal frame path.
- Audit executor lifecycle, cancellation and repeated launch/stop cleanup.

Exit criteria: no known N+1 query remains in a library page or launch path, and 30 launch/stop cycles show no ANR, worker leak or unbounded RSS growth.

## 4. OpenNative cockpit

- Redesign the secondary screen with complete OpenNative branding and clear Session, Performance and Shortcuts tabs.
- Keep the game isolated on the primary display and use the in-game drawer when Android exposes no presentation display.
- Preserve controller focus across dialogs, display hot-plug, rotation and activity recreation.
- Show actionable thermal, memory-pressure and shader-health states without permanent game-screen clutter.
- Validate touch targets, focus visibility, screen-reader labels and compact layouts.

Exit criteria: the cockpit survives 20 hot-plug/rotation cycles, every action is reachable by touch and controller, and loss of the second display returns controls to the primary display without ending the game.

## 5. Profiles and components

- Add **Share settings** for exporting either one game's container profile or a reusable global settings preset.
- Use a versioned, human-readable OpenNative manifest that round-trips every supported setting symmetrically.
- Strip credentials, account identifiers, saves, game files, local paths, device serials, logs and shader binaries from every shared export.
- Include compatibility metadata such as OpenNative version, profile schema, runtime type and required component versions without embedding those components.
- Show an export preview and an import diff grouped by graphics, runtime, controller, display and performance settings.
- Let recipients select categories, preserve unsupported fields for forward compatibility and choose merge or replace explicitly.
- Back up the destination before import and reject tampered, partial or incompatible packages atomically.
- Share through Android's system share sheet and import through the system file picker; no OpenNative account or hosted profile service is required.
- Inventory inherited runtime archives with source, license, size and SHA-256.
- Establish an OpenNative-controlled mirror only for components with verified redistribution rights.
- Preserve the last known-good component when download, verification or extraction fails.

Exit criteria: profile round trips preserve all supported settings, exported packages contain no private/device-local data, import conflicts are previewed and reversible, damaged components repair safely, and fresh/offline/upgrade installs have automated coverage.

## Device validation

- AYN Thor Max: Gamma Emerald and at least two games using different rendering/runtime paths.
- Five alternating warmed A/B runs per performance change.
- Cold and warm shader-cache captures.
- One 60-minute session with FPS, p95/p99, RSS, swap and temperature recorded.
- Generic ARM64 smoke test on at least one non-Qualcomm device before release.

## Release gate

Release only when user data remains intact during an in-place update, the full CI matrix is green, no severity-one or severity-two regression remains open, and all performance statements link to reproducible evidence.
