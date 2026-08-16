# OpenNative 1.0 Performance and Architecture Audit

Generated: 2026-08-16

This audit is based on the current `master` source and the measured AYN Thor Gamma Emerald capture.
Static inspection identifies candidates and correctness risks; it does not prove an FPS gain. A runtime
change is promoted only after alternating target-device A/B runs.

## Product contract

OpenNative 1.0 cannot promise that every Windows game will run perfectly across Wine, Proton, DXVK,
VKD3D, Box64, FEX and third-party Vulkan drivers. It can promise a verifiable compatibility contract:

- profiles are versioned, reversible and scoped to one game;
- saves and containers are never deleted by tuning or component updates;
- rendering, audio and input are checked together, not inferred from FPS;
- regressions have a one-action rollback to the last known-good component set;
- performance claims include the game build, scene, driver, runtime and thermal conditions;
- unsafe clocks, global Android changes and speculative affinity remain out of scope.

## Budgets for 1.0

| Area | 30 FPS target | 60 FPS target | Release requirement |
| --- | ---: | ---: | --- |
| Frame deadline | 33.33 ms | 16.67 ms | p95 and p99 reported, not average FPS alone |
| Frame-path managed allocations | 0 | 0 | Verified with allocation tracing |
| Frame-path filesystem work | 0 | 0 | Verified with Perfetto and StrictMode |
| Background CPU while a game is stable | <= 2% of one core | <= 2% of one core | Excludes the guest and graphics stack |
| Memory | No sustained swap growth | No sustained swap growth | No low-memory kill in a 60-minute run |
| Input | <= 1 presented-frame added latency | <= 1 presented-frame added latency | Controller, touch and dual-screen paths |
| Stability | 60 minutes | 60 minutes | No crash, ANR, deadlock or unbounded growth |

## Executive findings

### P0: public binary redistribution is unresolved

The README states that three inherited prebuilt shims are proprietary and lack a downstream
redistribution grant, while a public APK is currently attached to the beta release. This is a release
blocker, not a performance issue. Before 1.0, obtain permission, replace the binaries with compatible
builds, or publish source-only artifacts.

### P1: CPU translation, heat and memory pressure are the measured limit

The current Gamma Emerald capture averaged 23.28 FPS, 75.39% CPU and 31% GPU, with CPU/GPU peaks of
88 C/84 C. RSS reached roughly 3.1-3.3 GB while Android had about 600 MB available and more than
2.2 GB of swap in use. That evidence points first to CPU translation, thermal throttling and system
memory pressure, not raw GPU saturation.

The current 4096 MB guest graphics budget is a useful guardrail, but it is not a complete memory
governor. OpenNative does not yet attribute working set and swap growth to the guest, renderer,
translation runtime, Java heap and retained UI resources independently.

### P1: the compatibility presentation path still performs per-frame conversion work

When `sfCompatMode` is enabled, every submitted frame acquires a converted destination buffer and
queues BGRA-to-RGBA GPU work in
`app/src/main/cpp/asurfacerenderer/ASurfaceRendererContext.cpp:350-428`. Completion then creates a
SurfaceControl transaction and callback in the same file at `458-570`.

The hot path still includes:

- a JNI global reference per converted frame at `377`;
- method/class lookup when returning source fences at `300-319`;
- pool and window-map locks at `341`, `391`, `516` and `596`;
- a vector plus heap-owned transaction callback context at `558` and `711-730`;
- a new SurfaceControl transaction for each presented converted buffer at `532-569`.

Normal conversion is asynchronous; the visible `future.get()` calls in `blit_converter.cpp` are not
per-frame conversion waits. However, the first destination slots for a window are allocated and
synchronously registered at `ASurfaceRendererContext.cpp:1058-1085`, which can create first-frame or
resize hitches. Direct scanout already exists, but effects and some compatibility cases force the
compositor path. Perfetto and GPU traces must quantify each path before replacement.

### P1: process and runtime ownership can leak work

`ProcessHelper` creates a new single-thread executor for each debug stream and process wait at
`app/src/main/java/com/winlator/core/ProcessHelper.java:417-477` without explicit executor ownership or
shutdown. `PRINT_DEBUG` is hardcoded to `true`, so every guest line is printed at `422` and `452`; the
typed-stream overload also sends every line through Android logging at `445-448`. Long sessions and
repeated launches can therefore retain threads or spend time formatting guest output.

`PluviaApp` keeps the X environment, renderer view, input views and watcher in process-wide static fields
at `app/src/main/java/app/gamenative/PluviaApp.kt:188-200`; the source itself marks this as a leak risk.
The shared teardown is improved, but 1.0 needs one idempotent session owner instead of nullable globals.

### Resolved in 1.0.0-rc.1: confirmed collection N+1 database work

The audited loops previously executed one or more Room queries per item:

- app-change filtering calls `findApp()` per change in `SteamService.kt:4383-4397`;
- PICS app processing calls `findApp()` and then `findLicense()` per app at `4464-4468`;
- package processing calls `findApp()` per app ID, and may call `findLicense()` again, at `4561-4594`;
- DLC ownership checks call license and app lookups per depot at `806-828`.

They now use chunked bulk reads that stay below SQLite's 999-variable limit, in-memory maps and grouped
writes. DAO tests cover batches above 999 IDs. These paths remain outside the frame loop; synthetic
query-count and active-game contention measurements are still required for stable certification.

### P2: the Steam schema performs non-indexable work and can publish stale rows

`SteamApp` stores large maps and lists in one Room row. DLC/license queries in `SteamAppDao.kt:156-170`
search serialized `app_ids` with nested `REPLACE` and leading-wildcard `LIKE`, preventing useful indexes.
The main owned-app query sorts on `LOWER(name)` and has no matching expression index.

`getAllOwnedApps()` observes only a count and explicitly notes at `SteamAppDao.kt:111-124` that name,
icon and other property-only updates do not re-emit. This is both a freshness problem and a reason for
manual reload work.

### P2: custom-game discovery repeats directory work and can block through `runBlocking`

`CustomGameScanner.scanAsLibraryItems()` enumerates every configured root and recreates each item at
`CustomGameScanner.kt:587-610`. `createLibraryItemFromFolder()` can perform Steam matching and a
`runBlocking` database insert at `681-747`. Search, downloads and library refresh can each trigger scans.
Icon extraction is deduplicated, but discovery itself is not backed by a persistent index or file-change
journal.

### P2: observability still has a permanent session cost

The collector wakes every 500 ms, copies the frame ring, sorts deltas and creates a snapshot at
`PerformanceMetricsCollector.kt:149-210`. Resource reads run every second, thermals and JSONL output every
two seconds. Logging is buffered and no longer flushes every sample, but it remains active for the whole
session even when the HUD is hidden because Adaptive Engine consumes it.

This is not currently proven to be a material bottleneck. 1.0 should measure it and provide explicit
`Off`, `Light`, `Adaptive` and `Capture` telemetry levels. Fixed mode should not pay for model training or
disk output unless the user starts a capture.

### P2: release logging and startup work are not game-aware

`XServerScreen()` logs at INFO directly in the composable body at
`app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt:345-364`, so recomposition can emit
repeated release logs. The release logger accepts INFO and above.

Application startup also initializes frontend synchronization, migration, component preloading,
analytics and integrity warmup in `PluviaApp.kt:73-132`. These jobs are reasonable individually but do not
share a game-session I/O/CPU budget. Immediate game launch can therefore overlap with work that is not
required to present the first frame.

### P2: native build policy is inconsistent and not evidence-gated

Native targets mix global `-O2`, `-O3`, per-target flags and `-ffast-math`; for example
`app/src/main/cpp/asurfacerenderer/CMakeLists.txt:19-66`. The release APK is also signed with the debug key
in `app/build.gradle.kts:172-176`. The inherited CI workflows still contain upstream-specific actor,
secret and publication assumptions.

The roadmap must introduce comparable generic ARM64, ThinLTO and optional ARMv9 experiments, but no build
flag becomes default without binary-size, correctness, FPS, p95/p99 and thermal evidence.

## Implemented for 1.0.0-rc.1

- DXVK, graphics-driver and PulseAudio files are no longer force-extracted on every launch. A versioned
  marker is accepted only while backend-specific critical files exist and are non-empty; damage triggers
  repair and PulseAudio promotion preserves the previous directory on failure.
- Active shader-cache statistics are written after a clean session and consumed once at the next launch
  when all backend root timestamps still match. Crash, cache mutation, pruning and deletion paths do not
  trust the snapshot.
- Swap and Linux PSI memory pressure are sampled without `readLines` on the slow resource cadence.
  Adaptive restrictions require sustained pressure and recover only after a longer healthy window.
- Confirmed collection/database N+1 paths are batched, including Steam PICS, packages, DLC, branches and
  Nexus mod status/profile operations.

These are correctness and overhead improvements. They are not yet evidence of a game FPS increase.

## Implemented for 1.0.0

- A clean shutdown now writes a bounded manifest of the 64 most recently modified active shader-cache
  files while performing the existing end-of-session scan. The launch path validates generation,
  backend root stamps, canonical containment, file size and modification time before using an entry.
- Read-ahead is memory-aware and limited to 0, 4, 8 or 16 MiB. It uses one 256 KiB direct buffer and is
  disabled for cold caches, unknown memory state, Android low-memory state or less than 1.5 GiB free.
  No network shader-cache source was added because foreign caches cannot be proven compatible or safe.
- Normal sessions still calculate bounded in-memory frame and pressure metrics for the Adaptive Engine,
  but no longer format or persist JSONL samples every two seconds. Persistent capture and periodic
  metric logs are enabled only by a diagnostic launch.
- The X server startup log moved from the Compose body into an `appId`-keyed launch effect, preventing
  repeated INFO logging during recomposition.
- Snapshot, cleanup and Shader Health state transitions are serialized at session boundaries so guest
  termination cannot race UI teardown. The renderer and frame-statistics paths acquire no new lock.
- Unit tests cover warmup memory tiers, byte/file caps, changed-file rejection and canonical path escape.

### Expected impact and measurement status

The disk-write removal eliminates a deterministic background I/O path. Shader read-ahead is intended to
reduce first-use cache misses after a clean session, but its end-to-end effect remains game and storage
dependent. Neither change is described as an FPS gain until alternating cold/warm A/B captures complete.

## What is already in good shape

- Frame timestamps use a bounded, allocation-free ring on the render path.
- Metrics reuse scratch arrays and sample slow resources less often than frame statistics.
- GOG, Epic and Amazon refreshes batch existing-row lookups before upsert.
- Shader caches are separated by backend compatibility generation and scanned only at session boundaries.
- Converted-frame submission drops stale work when its pool is saturated instead of blocking the frame
  producer.
- Adaptive resolution defaults to observation, stages changes for the next launch and rolls back failed
  probes.
- Container data and the current OpenNative application ID are preserved by in-place updates.

## Required runtime evidence

The following remain hypotheses until measured on the AYN Thor:

1. Direct AHardwareBuffer scanout versus BGRA-to-RGBA compatibility conversion cost.
2. FEX versus Box64 host-thread cost for the same game and scene.
3. Android Performance Hint benefit and overhead with stable native thread IDs.
4. Shader compilation versus Unreal asset streaming versus translation-cache misses.
5. Telemetry overhead in `Off`, `Light`, `Adaptive` and `Capture` modes.
6. Generic ARM64 versus ThinLTO, ARMv9 and ARMv9 plus ThinLTO builds.

## Measurement protocol

- Use the same APK commit, game build, save, scene, container profile, driver and fan mode.
- Warm the device and alternate A/B variants to reduce thermal-order bias.
- Run five 60-second captures per variant after one warmup run.
- Record FPS, p50/p95/p99/max frametime, RSS/PSS, swap, CPU by thread, GPU load, clocks, thermal status,
  shader events, frame drops, audio underruns and input latency.
- Keep raw reports local; publish only sanitized summaries.
- Reject a change that improves average FPS while worsening p95/p99, rendering, audio, input or saves.

## Audit verdict

The highest-value 1.0 work is not a larger pile of environment variables. It is deterministic session
ownership, batch data access, measured translation and presentation paths, memory-pressure control,
shader-stutter attribution, and a reproducible compatibility/release system. The detailed delivery order
is defined in `docs/ROADMAP_1.0.0.md`.
