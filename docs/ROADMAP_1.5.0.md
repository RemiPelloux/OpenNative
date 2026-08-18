# OpenNative 1.5.0 Roadmap

## Goal

`1.5.0` is a stabilization and handheld-quality milestone built on the existing package and runtime architecture. It must improve confidence, frame pacing and daily usability without migrating user data or introducing unsafe device tuning.

## Delivery sequence

### Stage 0: contracts and foundations

- Finalize Room schemas for provider tabs, feed snapshots, transfer jobs and installation receipts.
- Add migrations, typed provider errors, HTTPS/redirect policy and bounded JSON/archive parsers.
- Introduce the Keystore-backed secret store and prove redaction in logs, diagnostics and settings exports.
- Build fake provider and AllDebrid servers for deterministic tests without real credentials.

Gate: schemas survive upgrade/downgrade fixtures, malformed inputs fail closed and no credential reaches persistent entities or reports.

### Stage 1: provider tabs and catalog

- Add the `+` immediately after **Custom**, the three-step creator and persisted tab ordering.
- Ship read-only metadata feeds first, with paging, ETag/Last-Modified, bulk upserts and stale offline snapshots.
- Add search, refresh, trust state and provider edit/disable/delete controls.

Gate: existing sources and launches are unchanged, recomposition performs no network/database request, and a 10,000-item fixture stays responsive.

### Stage 2: resolution and transfers

- Add the AllDebrid adapter behind the generic resolver contract.
- Add foreground download jobs with `.partial` staging, pause/resume/retry/cancel and storage reservation.
- Persist state transitions and recover safely after activity/process recreation.

Gate: fake-server authentication, 429, timeout, redirect and malformed-response cases pass; interrupted downloads resume or fail recoverably.

### Stage 3: portable installations

- Classify and inspect archives, reject traversal/unsafe links and extract only into managed staging.
- Verify hashes and required files, then promote atomically or through verified copy-and-swap.
- Reuse custom-game executable filtering and require launch-candidate review.

Gate: no archive can escape staging, failed promotion preserves the previous destination and cleanup remains locked.

### Stage 4: Windows Installer Manager

- Run valid PE `.exe` and `.msi` payloads in a new or explicitly selected Wine container.
- Track spawned Wine processes and filesystem quiescence instead of trusting the initial process exit.
- Surface reboot, timeout and needs-review states; discover but never auto-select the final game executable.

Gate: parent-exits-first, child hang, user cancel, reboot-required and missing-executable fixtures all preserve recovery material.

### Stage 5: cleanup, sharing and recovery

- Enable cleanup policies only after verification and installation-receipt commit.
- Add provider-job history, orphan staging detection and explicit repair/cleanup actions.
- Complete redacted settings/profile export, diff preview, merge/replace and rollback.

Gate: no automated path can delete an unverified installer or pre-existing game directory; profile round trips are symmetric.

### Stage 6: polish and certification

- Complete provider/controller accessibility, secondary-screen cockpit and lifecycle behavior.
- Profile feed, transfer, hashing, install and gameplay concurrency on the AYN Thor.
- Run the full CI, launch-cycle, soak and migration matrices before release.

Gate: all release criteria at the end of this document pass with sanitized evidence.

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
- Reuse conversion buffers by compatible dimensions and format, cap queue depth and drop obsolete frames before conversion when the producer outruns presentation.
- Move allocation, cache inspection and diagnostic formatting out of frame callbacks; add counters proving that the steady-state presentation path allocates nothing.
- Compare FIFO, mailbox and frame-cap interactions per title without forcing a global present mode.
- Correlate DXVK pipeline creation, Mesa/VKD3D cache writes, Unreal asset streaming and translation-runtime stalls.
- Persist pipeline/cache compatibility decisions once per session instead of recalculating them for every process or window.
- Expose per-title cache generation, size, last clean shutdown and invalidation reason.
- Keep cache repair explicit, path-confined and deferred until the guest exits.

Exit criteria: each promoted change improves median throughput by at least 3%, clearly reduces p95/p99 frametime, or lowers sustained power/temperature without a regression above 2% in another representative workload.

## 3. Runtime and data efficiency

- Add query-count regression tests to remaining Room and store-library aggregation paths.
- Replace per-item reads/writes with bounded bulk operations where profiling confirms N+1 behavior.
- Add query-count budgets for library refresh, source synchronization and configuration-catalog screens.
- Cache immutable launch metadata and avoid rebuilding container state during Compose recomposition.
- Use stable UI models and keyed lazy lists so progress updates do not recompose an entire game/source library.
- Debounce profile/session persistence and keep diagnostics off the normal frame path.
- Stream downloads and archive verification to disk with bounded buffers; never hold complete packages in memory.
- Deduplicate concurrent metadata, artwork, profile and component requests through one in-flight request per cache key.
- Use ETag/Last-Modified caches, exponential backoff and paging for every remote source.
- Delay nonessential library scans, artwork decoding and database maintenance while a game session is active.
- Audit executor lifecycle, cancellation and repeated launch/stop cleanup.

Exit criteria: no known N+1 query remains in a library page or launch path, and 30 launch/stop cycles show no ANR, worker leak or unbounded RSS growth.

### Provider and installer performance budgets

- No feed refresh, database query, hashing or file copy starts from Compose recomposition.
- At most one metadata refresh per provider tab and one resolver request per selected item.
- Feed pages are bulk-upserted in one transaction; no per-item lookup/write loop is accepted.
- Transfer progress is sampled for UI at a bounded cadence so byte-level callbacks cannot recompose the library.
- Download, hash and copy stages share fixed-size buffers and never retain a complete installer/archive in memory.
- Installer discovery walks only the selected destination and compares bounded metadata before hashing candidates.
- Artwork and nonessential catalog work pause while a game or Wine installation session owns foreground resources.
- Cleanup and orphan detection run after session exit with explicit I/O budgets and cancellation.

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

## 6. User-created provider tabs

- Place a compact `+` action immediately after the built-in **Custom** library tab. It opens the provider-tab creator and is not itself a permanent `Sources` tab.
- Let the user create multiple named tabs, reorder them, disable them temporarily and delete them without changing built-in store or custom-game data.
- Keep provider tabs separate from the `GameSource` enum and store-launch contracts. Each tab owns a stable UUID, display name, feed configuration, destination and download policy.
- Configure a tab through three focused steps: **Identity**, **Provider**, then **Install**. Show a final connection and permission check before saving.
- Support a versioned HTTPS JSON feed for item metadata and links. Fetch metadata first, then show title, version, size, source, compatibility and expected hash before download.
- Cache feed pages with ETag/Last-Modified, paginate results, deduplicate refreshes and preserve the last valid snapshot when a provider is offline or malformed.
- Allow an optional AllDebrid account at app level or per tab. Validate the API key, encrypt it with an Android Keystore-backed secret store and expose test/revoke/delete actions.
- Resolve only the link selected by the user from that provider tab. Never include the AllDebrid key in the feed URL, Room entities, exports, logs, crash reports or diagnostics.
- Display resolution and transfer stages separately: `Resolving`, `Queued`, `Downloading`, `Verifying`, `Installing`, `Cleaning` and `Ready`.
- Use a foreground transfer service with bounded streaming, pause/resume/retry/cancel, storage reservation and a `.partial` staging file.
- Let each tab choose an installation directory through Android's system folder picker and persist only the granted tree URI. Never construct unrestricted filesystem paths from feed data.
- Validate archive paths, declared sizes and optional SHA-256 before extracting into a staging directory. Promote the installed directory atomically when possible.
- Add an Installer Manager that distinguishes portable archives, PE `.exe` installers and `.msi` packages by content, then runs Windows installers in a dedicated Wine session.
- Let the user create/select the target container and runtime before setup. Track the whole Wine process family and prefix quiescence so a parent installer exiting early cannot produce a false success.
- Discover final game executables inside the selected destination, exclude setup/uninstall/redist tools, and require review before creating the custom-game launch record.
- Preserve failed or incomplete setup sessions for review. Never attempt an automatic rollback inside a pre-existing shared Wine prefix.
- Offer cleanup policies `Keep installer`, `Delete after verified install` and `Ask after install`. Cleanup runs only after the final installed files pass verification; failures keep the installer and staging report.
- Never auto-launch an installed executable. The user reviews the detected `.exe`, creates or links a container and confirms its settings separately.
- Define the data contract, state machine, threat model and implementation boundaries in [`CUSTOM_PROVIDER_TABS.md`](CUSTOM_PROVIDER_TABS.md).

Exit criteria: provider tabs do not alter built-in sources, refresh has bounded network/database work, secrets never appear outside encrypted storage, folder access survives restart, failed/cancelled transfers are resumable or recoverable, and cleanup cannot delete an installer before a verified installation.

## Device validation

- AYN Thor Max: Gamma Emerald and at least two games using different rendering/runtime paths.
- Five alternating warmed A/B runs per performance change.
- Cold and warm shader-cache captures.
- One 60-minute session with FPS, p95/p99, RSS, swap and temperature recorded.
- Generic ARM64 smoke test on at least one non-Qualcomm device before release.
- Provider-tab tests for create/reorder/delete, offline mode, pagination, ETag, invalid JSON, hash failure, 429 backoff and persisted folder permission.
- Installer-session tests for `.exe`/`.msi`, spawned child processes, timeout/reboot states, executable discovery and cleanup gating.
- AllDebrid tests through a fake provider server only; CI and release tests never require a real user API key.

## Release gate

Release only when user data remains intact during an in-place update, the full CI matrix is green, no severity-one or severity-two regression remains open, and all performance statements link to reproducible evidence.
