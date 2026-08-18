<div align="center">

<img src="docs/brand/opennative-mark.svg" alt="OpenNative" width="112" />

# OpenNative

**A performance-focused Android runtime for PC games you own.**

[![Release](https://img.shields.io/github/v/release/RemiPelloux/OpenNative?style=flat-square&logo=github)](https://github.com/RemiPelloux/OpenNative/releases)
[![Android](https://img.shields.io/badge/Android-ARM64-3DDC84?style=flat-square&logo=android&logoColor=white)](#compatibility)
[![License](https://img.shields.io/badge/license-GPL--3.0-36C5F0?style=flat-square)](LICENSE)

[Install](#install) · [Features](#features) · [Build](#build-from-source) · [Roadmap](ROADMAP.md) · [Changelog](CHANGELOG.md)

</div>

OpenNative combines Wine, Proton, DXVK, VKD3D, Mesa, Box64 and FEX in an Android interface designed for gaming handhelds. It focuses on stable frame delivery, low background overhead, controller reliability, portable game profiles and secondary-display controls.

OpenNative does not include games, firmware or store credentials. Use it only with software and accounts you are authorized to use.

## Features

- **Unified PC library:** Steam, Epic, GOG, Amazon and locally owned custom executables in one handheld interface.
- **Portable game profiles:** versioned Wine, graphics, controller and display settings without embedded device paths.
- **Persistent shader state:** independent per-game DXVK, Mesa/Zink and VKD3D cache generations with safe invalidation.
- **Faster reliable launches:** healthy runtime components are reused and damaged installations are repaired through staging.
- **Shader Health:** cold/warm state, bounded local warmup and per-title maintenance that runs only after game exit.
- **Adaptive Engine:** observation-first performance diagnosis and opt-in next-launch resolution changes with rollback.
- **Low-overhead diagnostics:** expensive metrics and disk capture remain outside normal gameplay unless requested.
- **Handheld cockpit:** performance and session controls can move to an Android secondary display with a drawer fallback.
- **Efficient libraries:** known Room/store N+1 paths use bulk reads and grouped writes.
- **Private reports:** diagnostic exports redact credentials, saves, game binaries and local paths.

OpenNative never downloads third-party shader caches, forces device clocks, changes Android globally or promises a universal FPS gain. Performance changes must pass controlled before/after measurements.

## Coming in 1.5.0

These features are planned and are **not included in the current `1.1.0` release**:

- A compact `+` after **Custom** for creating named, reorderable provider tabs.
- Versioned HTTPS feeds with pagination, offline snapshots and visible trust/compatibility metadata.
- Optional AllDebrid link resolution with Android Keystore-backed credential storage.
- A resumable foreground download queue with verification, storage reservation and recovery after process death.
- A Wine Installer Manager for portable archives, `.exe` setup programs and `.msi` packages.
- Dedicated installation containers, complete Wine process-tree tracking and final game-executable discovery.
- Safe installer cleanup only after the installed game and launch record have been verified.
- Shareable per-game and global settings presets with redaction, import previews and reversible conflict handling.
- A redesigned OpenNative secondary-screen cockpit with stronger controller and hot-plug behavior.

See the [1.5.0 delivery plan](docs/ROADMAP_1.5.0.md) and [provider/installer specification](docs/CUSTOM_PROVIDER_TABS.md).

## Compatibility

| Target | Status |
| --- | --- |
| Android ARM64, API 29+ (`modern`) | Primary build |
| AYN Thor Max, Android 13 | Device-tested |
| Android secondary displays | Cockpit with drawer fallback |
| Legacy 32-bit Android | Buildable, not the primary performance target |
| Individual games | Depends on runtime, driver and title |

The application ID remains `com.remipelloux.gamenativecustom` so existing OpenNative installations retain app-private containers and saves. The internal `app.gamenative` namespace and historical storage identifiers are also preserved until a tested migration exists.

## Install

Download the current ARM64 APK from [GitHub Releases](https://github.com/RemiPelloux/OpenNative/releases). Install it over an existing OpenNative build signed with the same certificate; do not uninstall first when app-private containers or saves must be preserved.

Before redistributing an APK, review [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES). Some inherited prebuilt components have separate terms, and the remaining binary-host dependency is documented in [OpenNative Independence](docs/INDEPENDENCE.md).

OpenNative has no third-party in-app updater. Releases are installed explicitly.

## Custom games

1. Extract the game into a folder Android can access.
2. Open **Library > Custom** and grant that folder with Android's picker.
3. Select the actual game executable in its container settings.
4. Start at 1280x720, 30 FPS and a compatibility-oriented translation profile.
5. Change one setting at a time and compare a repeatable scene.

The validated AYN Thor starting point for Gamma Emerald, including the optional shadow profile, is in [Gamma Emerald](docs/GAMMA_EMERALD.md).

## Build from source

Requirements:

- JDK 17
- Android SDK 36
- Android NDK `27.3.13750724`

```bash
git clone https://github.com/RemiPelloux/OpenNative.git
cd OpenNative
./gradlew :app:assembleModernDebug
./gradlew :app:assembleModernRelease
```

Optional API keys belong in `local.properties` or environment variables and must never be committed.

## Tests

The Android JVM suite uses an explicit ByteBuddy agent so Mockito and MockK work on JDKs that disable dynamic self-attachment.

Run a focused class while developing:

```bash
./gradlew :app:testModernDebugUnitTest --tests 'app.gamenative.performance.*'
```

Run the full modern JVM suite only when enough local disk and memory are available:

```bash
./gradlew :app:testModernDebugUnitTest
```

Gradle test/build outputs are disposable and can be removed with `./gradlew clean`. Performance work follows the evidence and promotion rules in [Performance Method](docs/PERFORMANCE.md).

## Documentation

- [Roadmap](ROADMAP.md): active milestones and links to the `1.5.0` and `2.0.0` plans.
- [Adaptive Engine](docs/ADAPTIVE_ENGINE.md): model, safeguards and resolution policy.
- [Performance Method](docs/PERFORMANCE.md): capture protocol and current Thor findings.
- [Gamma Emerald](docs/GAMMA_EMERALD.md): tested container baseline.
- [Custom provider tabs](docs/CUSTOM_PROVIDER_TABS.md): planned dynamic tabs, AllDebrid and safe installation pipeline.
- [Independence](docs/INDEPENDENCE.md): identifiers, attribution and binary-host migration.
- [Changelog](CHANGELOG.md): released changes since `0.1.0`.
- [Contributing](CONTRIBUTING.md): code, testing and evidence requirements.

## Privacy

OpenNative does not send gameplay feedback, compatibility telemetry, device statistics or recommendations to GameNative services. Store features communicate with the selected store provider. Read the [privacy policy](PrivacyPolicy/README.md), and remove private paths and tokens from bug reports.

## Credits and license

OpenNative derives from GameNative by Utkarsh Dalal and its contributors. It also depends on Wine, Proton, DXVK, VKD3D, Mesa, Box64, FEX and other open-source projects. Copyrights, license notices and Git history are preserved; attribution does not imply shared governance or endorsement.

OpenNative source is licensed under [GPL-3.0](LICENSE). Bundled components may use different licenses; consult [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES).
