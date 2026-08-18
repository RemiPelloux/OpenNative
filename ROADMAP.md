# OpenNative Roadmap

OpenNative prioritizes measured frame pacing, stability and handheld ergonomics. Items move from experimental to default only after repeatable A/B tests.

The milestone plan from the current beta through the stable `1.0.0` release is maintained in [docs/ROADMAP_1.0.0.md](docs/ROADMAP_1.0.0.md). The evidence and static findings behind that plan are in [docs/PERFORMANCE_AUDIT_1.0.0.md](docs/PERFORMANCE_AUDIT_1.0.0.md).

## Now

### 1. Frame delivery and shader path

- Capture Perfetto and GPU traces around the SurfaceFlinger compatibility path, Vulkan presentation, DXVK shader compilation and frame queue depth.
- Eliminate remaining avoidable copies and synchronization only when buffer ownership can be proven safe.
- Add shader-cache warmup visibility, cache health reporting and per-title cache reset instead of broad cache deletion.
- Measure mailbox versus FIFO presentation for 30 FPS titles; never force a mode globally.

### 2. Thermal stability on AYN Thor

- Establish five-run cold/warm baselines with FPS, p95/p99 frametime, RSS, CPU/GPU load and temperature.
- Add sustained thermal warnings and a user-confirmed lower-power profile; never change Android clocks or global settings.
- Run 30 launch/stop cycles and a 60-minute Gamma Emerald session without ANR, native crash or unbounded memory growth.

### 3. Runtime and data efficiency

- Audit remaining Room and store-library flows for N+1 queries with query-count tests.
- Remove diagnostic Box64/guest rewrite logging from production paths unless explicitly enabled.
- Cache immutable container metadata and avoid reconstructing expensive launch configuration during Compose recomposition.
- Batch or debounce disk writes from metrics, session state and profile changes.

## Next

- Rework the secondary-screen cockpit with complete OpenNative branding, a polished visual hierarchy, clearer performance and session states, and consistent touch/controller navigation.
- Make the secondary-display cockpit resilient to hot-plug, rotation and activity recreation.
- Add per-game performance presets with explicit inheritance and reversible overrides.
- Export sanitized diagnostic bundles that exclude credentials, saves, game paths and protected content.
- Add release CI for lint, focused unit tests, reproducible APK builds, checksums and SBOM generation.
- Provide a migration assistant before adopting a final `com.remipelloux.opennative` package ID.

## Later

- Compare FEX and Box64 presets by workload rather than device marketing name.
- Test experimental refresh-rate matching for capped games with power measurements.
- Upstream generic renderer, controller and database fixes when they are independent of OpenNative branding.
- Expand testing beyond Snapdragon/Turnip to additional Android ARM64 GPUs.

## Promotion criteria

A performance change needs five warmed runs per variant on the same scene and configuration. Promote it when it improves median FPS by at least 3%, clearly improves p95/p99 frametime, or reduces power/temperature without a meaningful performance loss. Reject changes with crashes, visual corruption, input regressions or more than 2% regression in another representative workload.
