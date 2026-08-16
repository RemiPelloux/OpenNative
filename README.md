<div align="center">

# OpenNative

**Run the PC games you own on Android, with a performance-focused experience for gaming handhelds.**

[![Release](https://img.shields.io/github/v/release/RemiPelloux/OpenNative?style=flat-square&logo=github)](https://github.com/RemiPelloux/OpenNative/releases)
[![Build](https://img.shields.io/badge/Android-ARM64-3DDC84?style=flat-square&logo=android&logoColor=white)](#build-from-source)
[![License](https://img.shields.io/badge/license-GPL--3.0-36C5F0?style=flat-square)](LICENSE)
[![Upstream](https://img.shields.io/badge/based_on-GameNative-F4C542?style=flat-square)](https://github.com/utkarshdalal/GameNative)

[Features](#what-opennative-adds) · [Install](#install) · [Gamma Emerald](docs/GAMMA_EMERALD.md) · [Performance](docs/PERFORMANCE.md) · [1.0 Roadmap](docs/ROADMAP_1.0.0.md) · [Roadmap](ROADMAP.md)

</div>

OpenNative is an independent, experimental fork of [GameNative](https://github.com/utkarshdalal/GameNative). It keeps the original Android/Wine stack and store integrations while concentrating on frame pacing, lower background overhead, controller reliability, portable container profiles and dual-screen handhelds such as the AYN Thor.

This project does not include games. Use it only with software and store accounts you are authorized to use.

## What OpenNative adds

- **Non-blocking SurfaceFlinger compatibility path:** BGRA-to-RGBA conversion is queued asynchronously, saturated pools drop stale frames instead of waiting on the presentation thread, and redundant GL state changes are cached.
- **Lower telemetry overhead:** expensive sensors are sampled less often, `/proc` and sysfs counters use allocation-light parsers, and JSONL session logs are buffered instead of flushed every 500 ms.
- **Dual-screen performance cockpit:** the game stays on the primary display while metrics and session shortcuts can use an Android secondary display.
- **Portable container profiles:** versioned exports preserve relevant Wine, graphics, controller and display settings without embedding device-local paths.
- **Controller runtime fixes:** only configured controller slots create guest-side workers; single-player containers no longer provision four players by default.
- **Fewer database round trips:** GOG upserts, storage migrations and frontend synchronization use batch reads or set-based updates instead of row-by-row N+1 access.
- **Gamma Emerald validation:** a stable 720p/30 FPS baseline with controller input and an optional low-cost shadow profile is documented separately.
- **Measured Thor tuning:** affinity is opt-in, native and WoW64 masks are respected, repeated mod writes are batched, and duplicate custom-game icon work is suppressed during library refreshes.
- **UE shader and memory stability:** DXVK caches now follow OpenNative's real package path, unlimited profiles receive a conservative device-aware guest-memory budget on 6-14 GB devices, and retired SurfaceFlinger buffers are reclaimed.
- **Backend-aware shader cache:** DXVK, Mesa/Zink and VKD3D caches persist per game with independent compatibility keys. A Turnip update keeps reusable DXVK state while rotating driver-specific Vulkan caches; existing OpenNative caches migrate without a copy and custom paths remain untouched.
- **Adaptive Engine 0.3:** classifies CPU, GPU, memory, thermal and pacing pressure; predicts five-second p95/temperature; identifies per-game frame cost with bounded RLS; and stages aspect-correct resolution changes for the next launch with confidence, hysteresis, cooldown and rollback. Existing games default to observation mode.
- **Shader Health:** shows cold/warm/growing state, active generation and cache growth in the quick menu and Thor cockpit. Explicit maintenance runs only after game exit and never removes the active Mesa, DXVK or VKD3D generation.
- **Capability-driven Snapdragon support:** records Qualcomm/Adreno, CPU policy topology and Android Performance Hint availability from runtime capabilities. OpenNative does not force clocks, affinity, unsafe math or speculative driver variables.
- **Sanitized diagnostics:** shared reports include performance, prediction, memory, resolution and shader state while redacting credentials and device-local paths.

These changes have automated coverage and device smoke testing. They are not presented as a universal FPS uplift: performance claims require controlled before/after captures on the same game, scene, driver and thermal state.

The `0.3.0` architecture and safety model are documented in [`docs/ADAPTIVE_ENGINE.md`](docs/ADAPTIVE_ENGINE.md); implementation and release gates are tracked in [`docs/ROADMAP_0.3.0.md`](docs/ROADMAP_0.3.0.md).

The path to a stable `1.0.0`, including renderer, translation, shader, memory, N+1, compatibility and release gates, is documented in [`docs/ROADMAP_1.0.0.md`](docs/ROADMAP_1.0.0.md). Its source audit is in [`docs/PERFORMANCE_AUDIT_1.0.0.md`](docs/PERFORMANCE_AUDIT_1.0.0.md).

## Compatibility

| Target | Status |
| --- | --- |
| Android ARM64, API 29+ (`modern`) | Primary build |
| AYN Thor Max, Android 13 | Device-tested |
| Steam, Epic, GOG, Amazon and custom games | Inherited from GameNative; compatibility varies by title |
| Android secondary displays | Cockpit with in-game drawer fallback |
| Legacy 32-bit Android flavor | Buildable but not the current performance target |

OpenNative deliberately keeps the existing application ID `com.remipelloux.gamenativecustom` and the historical public storage directory `GameNative`. This lets the release update the current test installation without orphaning its containers or Gamma Emerald profile. Kotlin/Java namespaces also remain `app.gamenative` to avoid a large, risk-only refactor.

## Install

The public `0.3.0-beta.1` prerelease currently includes a tested `modernRelease`
APK for the AYN Thor development device. Redistribution of that binary is under
review: the inherited bundle contains three upstream prebuilt shims marked
proprietary and does not include a downstream redistribution grant. Stable
binary releases require permission from their copyright holder or compatible
replacements; source remains available for permitted local builds.

Developers can build the source below where permitted. Install it over the
current OpenNative test build; do not uninstall first if you need to preserve
app-private containers. Always verify the application ID and signing
certificate before an in-place update.

The fork disables GameNative's built-in APK updater so an upstream package cannot silently replace OpenNative. Updates are installed explicitly from this repository.

## Configure a custom game

1. Extract the game into a user-accessible folder.
2. In **Library > Custom**, grant access with Android's folder picker.
3. Open the game settings and select its actual `.exe`.
4. Start with a conservative 1280x720 container, a 30 FPS cap and a compatibility-oriented Box64/FEX profile.
5. Change one graphics option at a time and measure a repeatable scene.

The tested Gamma Emerald recipe and shadow trade-offs are in [docs/GAMMA_EMERALD.md](docs/GAMMA_EMERALD.md).

## Build from source

Requirements: JDK 17, Android SDK 36 and Android NDK `27.3.13750724`.

```bash
git clone https://github.com/RemiPelloux/OpenNative.git
cd OpenNative
./gradlew :app:assembleModernDebug
./gradlew :app:assembleModernRelease
```

Optional API keys belong in `local.properties` or environment variables and must never be committed:

```properties
STEAMGRIDDB_API_KEY=your_key
```

The `modernRelease` build is minified and resource-shrunk. The first preview release intentionally keeps the existing local signing identity so it can update the Thor test installation; it is not a Play Store signing setup.

## Test

Run the focused JVM suite:

```bash
./gradlew \
  :app:testModernDebugUnitTest \
  --tests '*GOGGameDaoTest' \
  --tests '*FrontendSyncManagerTest' \
  --tests '*ControllerManagerTest' \
  --tests '*MetricsSamplingCadenceTest' \
  --tests '*JsonlSessionLogTest' \
  --tests '*PortableContainerProfileTest' \
  --tests '*ExternalDisplayInputControllerTest'
```

For the 0.3.0 engine and shader cache:

```bash
./gradlew :app:testModernDebugUnitTest \
  --tests 'app.gamenative.performance.*' \
  --tests 'com.winlator.core.ShaderCache*' \
  --tests 'app.gamenative.utils.DiagnosticsLogTest'
```

For performance work, follow [docs/PERFORMANCE.md](docs/PERFORMANCE.md). A change is promoted only when repeated measurements show a gain without a stability or thermal regression.

## Privacy and services

OpenNative inherits optional integrations and analytics code from GameNative. Store login, compatibility and component-download features may contact Steam, Epic, GOG, Amazon, Nexus Mods or GameNative-operated endpoints. Review [PrivacyPolicy/README.md](PrivacyPolicy/README.md) and the source before using online features. Never attach credentials, tokens, game files or app-private data to bug reports.

## Contributing

Issues and focused pull requests are welcome. Include the device, Android version, SoC/GPU, driver, game version, exact container profile, reproduction steps and logs with private paths removed. Performance pull requests must include before/after evidence.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the review and validation rules.

## Credits and license

OpenNative is based on GameNative by Utkarsh Dalal and its contributors, and in turn uses Wine, Proton, DXVK, VKD3D, Mesa, Box64, FEX and other projects. Original copyrights and history are preserved.

The application source remains licensed under [GPL-3.0](LICENSE). Bundled components can have different terms; read [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES) before redistributing an APK.
