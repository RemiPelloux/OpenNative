# OpenNative 1.0.0 Roadmap

OpenNative 1.0 is the first stability and compatibility release whose performance claims are backed by
repeatable device evidence. It is not a promise that every Windows game runs perfectly. It is a promise
that supported games have reproducible profiles, safe updates, measurable behavior and reliable rollback.

The supporting source audit is in `docs/PERFORMANCE_AUDIT_1.0.0.md`.

## Non-negotiable rules

- Preserve saves, game files and existing containers across every migration and update.
- Keep generic Android ARM64 behavior correct; Thor support is capability-driven, not model-name gated.
- Never force clocks, fan firmware, unsafe affinity, fast-math or driver variables globally.
- Measure before optimizing and change one variable per A/B experiment.
- Do not distribute proprietary binaries without an explicit redistribution right.
- Do not call a workaround a universal optimization.

## 0.4: measurement and session isolation

### Runtime observability

- Add Perfetto trace sections for guest launch, translation, shader compilation, SurfaceControl submit,
  conversion queue, audio callback, input dispatch, storage and shutdown.
- Add native counters for submitted, converted, dropped and presented frames, queue depth, fence wait and
  first-buffer registration time.
- Add telemetry levels: `Off`, `Light`, `Adaptive` and `Capture`. Fixed mode performs no model training or
  session-file writes unless Capture is selected.
- Replace repeated full delta sorting with a measured bounded histogram or selection algorithm only if
  profiling shows the current 500 ms collector is material.
- Export one sanitized bundle containing configuration fingerprints, component versions and aggregated
  metrics, never credentials, saves, game paths or shader binaries.

### Session owner

- Replace process-wide nullable runtime globals with one explicit `GameSession` state machine:
  `Preparing`, `Launching`, `Running`, `Paused`, `Stopping`, `Stopped`, `Failed`.
- Make start/stop idempotent and give every coroutine, executor, native context, receiver and service a
  session-owned lifetime.
- Replace per-process ad-hoc executors in `ProcessHelper` with bounded shared executors and cancellable
  stream pumps. Disable guest stdout printing in release unless a capture is active.
- Move composable-body logs into effects or state transitions.
- Add a game-session scheduler that pauses or deprioritizes store refresh, icon extraction, component
  preload, cloud scans and frontend export while the guest is running.

### Gate

- Ten launch/pause/resume/stop cycles pass with no retained session object or thread growth.
- Capture mode overhead is measured; Off and Light are below 2% of one host core in a stable scene.
- No startup background job delays first frame by more than 50 ms at p95.

## 0.5: frame delivery and renderer architecture

### Zero-copy presentation program

- Trace direct scanout, Vulkan compositor and `sfCompatMode` conversion separately.
- Cache JNI method IDs and avoid per-frame class lookup.
- Pool transaction callback contexts and release lists when ownership rules permit it.
- Pre-register bounded destination buffers before the first visible frame or resize, removing synchronous
  registration from first-use submission.
- Add queue backpressure metrics and per-window limits; continue dropping stale frames instead of waiting
  on the producer.
- Evaluate direct AHardwareBuffer presentation and format negotiation so conversion is skipped only when
  producer, consumer, format and fence ownership are all verified.
- Preserve the compositor path for scaling, color and effects; never silently disable visible features to
  claim a faster path.

### Frame pacing

- Unify FPS cap, display refresh hint, DXVK cap and guest cap into one per-title policy with conflict
  detection.
- Compare FIFO, mailbox and immediate modes only where the driver exposes them correctly.
- Report duplicated, dropped and late frames, not only average FPS.
- Add audio-clock and present-clock correlation to detect pacing fixes that create audio drift.

### Gate

- At least 15% lower conversion-path p95/p99 or a meaningful thermal reduction on the verified GPU-bound
  case, with no rendering corruption.
- No frame-path allocation, filesystem access or unbounded queue.
- Correct output across rotation, external display, effects, fullscreen/windowed transitions and resume.

## 0.6: translation, shaders and memory governor

### Translation runtime

- Profile FEX and Box64 by native thread with Simpleperf and Perfetto on identical saves and scenes.
- Build per-title runtime recommendations from measured workload classes, never from device marketing
  names.
- Evaluate Android Performance Hint sessions only after stable translation, presentation and compilation
  thread IDs are available. Keep it off when unsupported or when overhead cancels the gain.
- Version FEX/Box64 presets and keep the previous runtime available for one-action rollback.
- Reject speculative instruction flags and unsafe memory-order shortcuts without compatibility tests.

### Shader pipeline

- Add explicit DXVK/VKD3D/Mesa pipeline-compile event counters and separate them from asset streaming and
  translation-cache misses.
- Keep caches per game, backend, driver and runtime generation with atomic migration and size limits.
- Add warmup from caches generated by the same user/game/component fingerprint; do not distribute foreign
  or copyrighted shader data.
- Schedule cache inspection and pruning only before launch or after complete guest shutdown.
- Provide a Shader Doctor view: backend generation, cold/warm state, compilation spikes, invalidation
  reason, size and safe reset.

### Memory governor

- Track Java heap, native heap, guest RSS/PSS, graphics allocations, available memory, swap and PSI.
- Introduce staged pressure responses: trim cover art, stop background indexing, reduce diagnostics,
  reclaim retired render pools, then warn the user. Do not silently corrupt or terminate a save.
- Replace a single static graphics-memory budget with capability- and workload-aware bounds validated per
  runtime, while preserving explicit user overrides.
- Detect sustained swap growth and offer a lower profile for the next launch.

### Gate

- Gamma Emerald battle and traversal runs show no sustained swap growth after warmup.
- Cold and warm shader captures identify the source of every p99 spike above 100 ms.
- Runtime recommendation changes improve p95/p99 or thermals without more than 2% regression elsewhere.

## 0.7: data layer, N+1 removal and indexed library

### Steam batching

- Add bulk DAO reads for app IDs, package IDs and licenses.
- Replace per-item lookups in change filtering, PICS app processing, package processing and DLC ownership
  with preloaded maps inside bounded transactions.
- Batch stub inserts and package-ID updates instead of issuing an insert/update for every app.
- Add query-count tests with 10, 100, 1,000 and 10,000 synthetic catalog items.

### Schema redesign

- Normalize Steam app-package, app-license, DLC and depot relationships instead of querying serialized
  lists with `REPLACE` plus wildcard `LIKE`.
- Add measured indexes for package ID, DLC parent, install state and case-folded sort/search keys.
- Replace count-only library invalidation with versioned row changes or paging so metadata updates appear
  without a manual refresh.
- Use Room migrations with backup, validation, rollback and large-library timing tests.

### Custom games and storage

- Replace repeated root scans with a persistent custom-game index updated by explicit refresh, SAF change
  signals or bounded polling.
- Remove `runBlocking` from folder discovery and move Steam matching/database writes into cancellable IO.
- Cache executable and artwork metadata by directory fingerprint.
- Rate-limit cloud-save hashing, download verification, mod scans and frontend exports while a game runs.

### Gate

- No O(N) Room query count for any list or sync operation.
- A 10,000-item synthetic Steam library refresh stays within a defined query and memory budget.
- Library search and metadata updates remain responsive while a game is active.

## 0.8: compatibility profiles and player-facing recovery

### Versioned per-game profile

- Store game executable hash, game build, Wine/Proton, DXVK/VKD3D, driver, translation runtime, controller,
  audio, resolution and known-good status in a portable schema.
- Separate user choices from automatic recommendations so importing or updating never overwrites intent.
- Diff profile changes and support one-action rollback.
- Add component compatibility rules and block known-bad combinations with a clear explanation.

### Game Doctor

- Add a preflight check for executable, storage permission, runtime files, graphics driver, controller,
  audio and available space.
- After a crash, classify Android low-memory kill, native signal, Wine failure, driver failure and forced
  stop without pretending unknown causes are solved.
- Offer safe actions: restore known-good profile, clear only the active shader generation, lower next-launch
  memory/resolution, or export a sanitized report.

### Saves, input and audio

- Detect supported save roots and create atomic local snapshots before risky profile/component changes.
- Never bundle saves into diagnostics; restoration is explicit and previewed.
- Build controller conformance tests for D-pad, sticks, triggers, hot-plug, focus, Steam Input and XInput.
- Measure audio underruns and latency across PulseAudio/ALSA paths and suspend/resume.
- Make the Thor cockpit resilient to display hot-plug, rotation, lid state and activity recreation.

### Gate

- Profile import/export is symmetric and free of device-local paths.
- Save backup/restore passes interrupted-write and low-space tests.
- Gamma Emerald and the broader certification set pass controller, audio and dual-display scenarios.

## 0.9: release engineering and broad compatibility

### Build and supply chain

- Resolve redistribution rights for every bundled binary and generate an SBOM with source/license hashes.
- Replace inherited upstream-only workflows with OpenNative-owned CI and protected release jobs.
- Use a production signing key and document the migration from the current development signature/package.
- Produce reproducible generic ARM64 artifacts plus isolated ThinLTO and ARMv9 A/B variants.
- Record compiler, NDK, dependency and prebuilt-component fingerprints in every release.
- Audit `-O2`, `-O3`, `-ffast-math` and LTO per target; remove inconsistent or unproven flags.

### Compatibility matrix

- Certify at least one DX9, DX11, DX12, OpenGL and Unreal Engine title.
- Add non-Snapdragon ARM64 coverage before claiming general Android compatibility.
- Test Android 10 through the current target SDK, 60/90/120 Hz displays and 6/8/12/16 GB memory classes.
- Track game build, component fingerprint, result, known issues and last verified OpenNative version.

### Gate

- Clean CI builds, tests, lint, native sanitizers where supported, APK signature verification, checksum and
  SBOM all pass from the tagged commit.
- Upgrade and rollback preserve containers and saves.
- No unresolved critical redistribution or privacy issue.

## 1.0: certification and stable release

### Required scenarios

- Five alternating A/B runs after warmup for every promoted performance change.
- Gamma Emerald: boot, house dialogue, starter selection, traversal, battle, shader-cold and shader-warm.
- Thirty launch/stop cycles and a continuous 60-minute run on the AYN Thor Max.
- Pause/resume, rotation, primary/secondary display changes, controller reconnect and storage pressure.
- Cold install, upgrade from the current OpenNative package, profile migration and rollback.

### Stable-release criteria

- No known data-loss, save-corruption, security or redistribution blocker.
- No crash, ANR, deadlock or abnormal thread/memory growth in certification.
- No regression above 2% in a representative workload.
- A promoted optimization shows at least 3% repeatable throughput gain, clear p95/p99 improvement, or a
  meaningful power/temperature reduction.
- Supported-game status is tied to exact component and game fingerprints.
- Release notes state measured results and known limits without universal FPS claims.

## Priority order

1. Resolve binary redistribution and production release ownership.
2. Implement session ownership and remove process/thread/logging leaks.
3. Capture translation, memory and presentation traces on the Thor.
4. Remove Steam N+1 queries and normalize the worst schema paths.
5. Implement measured renderer, shader and memory changes.
6. Build compatibility profiles, Game Doctor and save-safe recovery.
7. Complete broad-device CI and 1.0 certification.

## Explicitly deferred unless evidence changes

- global CPU affinity or forced clocks;
- unsafe MMU or synchronization shortcuts;
- global `fast-math` expansion;
- downloaded shader caches from unknown game/component fingerprints;
- automatic profile changes during a running session;
- claims of universal Snapdragon or Unreal Engine optimization.
