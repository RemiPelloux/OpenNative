<div align="center">

<img src="docs/brand/opennative-mark.svg" alt="OpenNative logo" width="112" />

# OpenNative

**A performance-focused Windows compatibility environment for Android gaming handhelds.**

[![Release](https://img.shields.io/github/v/release/RemiPelloux/OpenNative?style=flat-square&logo=github)](https://github.com/RemiPelloux/OpenNative/releases)
[![Android](https://img.shields.io/badge/Android-ARM64-3DDC84?style=flat-square&logo=android&logoColor=white)](#platform-support)
[![License](https://img.shields.io/badge/license-GPL--3.0-36C5F0?style=flat-square)](LICENSE)

[Download](https://github.com/RemiPelloux/OpenNative/releases) · [Roadmap](ROADMAP.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md)

</div>

OpenNative brings Wine/Proton, DXVK, VKD3D, Mesa, Box64 and FEX together in an Android interface designed for handheld gaming. The project prioritizes predictable frame delivery, low background overhead, controller reliability, portable configuration and safe runtime management.

OpenNative is an independent open-source project. It does not include games, firmware, store credentials or third-party shader caches. Use it only with software, downloads and accounts you are authorized to access.

## Project status

| Channel | Version | Status |
| --- | --- | --- |
| Stable | `1.2.1` | FitGirl and Skidrow provider tabs, cover-grid catalog and verified installer cleanup |
| Next milestone | `1.5.0` | Settings sharing, cockpit polish and remaining certification |
| Architecture milestone | `2.0.0` | Long-term roadmap |

## Current capabilities

### Runtime and compatibility

- Unified Steam, Epic, GOG, Amazon and custom-executable library flows.
- User-created provider tabs after **Custom**, backed by a user-supplied HTTPS JSON or RSS/Atom feed. Launch seeds the bundled FitGirl and Skidrow tabs.
- Wine/Proton containers with Box64 and FEX translation options.
- Per-title graphics, runtime, controller and display configuration.
- Versioned portable profiles without embedded device-local paths.

### Performance and shaders

- Independent per-game DXVK, Mesa/Zink and VKD3D cache generations.
- Safe cache invalidation, local warmup and post-session maintenance.
- Adaptive Engine observation with opt-in next-launch resolution changes and rollback.
- Low-overhead metrics that avoid persistent diagnostic I/O during ordinary play.
- Batched reads and writes across known Room and store-library N+1 paths.

### Handheld experience

- Controller-aware navigation and custom-game executable management.
- Secondary-display performance cockpit with an in-game drawer fallback.
- Local performance, memory-pressure, thermal and Shader Health reporting.
- Sanitized diagnostic exports that exclude credentials, saves and game binaries.

Performance varies by game, runtime, driver and thermal conditions. OpenNative does not claim universal FPS improvements. Changes are promoted only after controlled before/after measurements.

## Shipped in 1.2.1

- A `+` after **Custom** creates a named provider tab. Launch seeds `assets/opennative-provider-tabs.json` (FitGirl and Skidrow).
- Provider tabs use a library-style cover grid. Tapping a title opens a detail modal with Download and Install to Custom.
- Feeds are user-supplied HTTPS JSON envelopes or generic RSS/Atom URLs. Daily refresh reads the latest three pages on app open; Settings also has a manual refresh.
- Optional AllDebrid resolution with a Keystore-backed key. The first download click can show a key modal; dismiss is allowed and Download stays disabled until a key validates.
- Resumable downloads, storage reservation (including Wine-prefix headroom), portable archive install, and installer deletion only after a verified receipt.

See [provider tabs](docs/CUSTOM_PROVIDER_TABS.md) and [feed contract](docs/PROVIDER_FEEDS.md).

## Planned for 1.5.0

The following work remains after `1.2.1`:

| Area | Planned capability |
| --- | --- |
| Wine setup sessions | Full process-tree tracking, reboot/timeout states and richer executable review |
| Settings sharing | Redacted per-game and global presets with diff preview, merge/replace and rollback |
| Cockpit | Refined OpenNative secondary-screen UX with robust controller, rotation and hot-plug behavior |
| Performance | Measured frame-delivery, shader, database, Compose and transfer-path optimization |

Implementation is gated by security, recovery and performance criteria. See the [1.5.0 roadmap](docs/ROADMAP_1.5.0.md).

## Platform support

| Target | Support level |
| --- | --- |
| Android ARM64, API 29+ (`modern`) | Primary release target |
| AYN Thor Max, Android 13 | Primary device-validation target |
| Android secondary displays | Supported through cockpit and drawer fallback |
| Legacy 32-bit Android | Buildable, not a primary optimization target |
| Individual Windows games | Compatibility depends on title and selected runtime stack |

## Installation

1. Download the current ARM64 APK from [GitHub Releases](https://github.com/RemiPelloux/OpenNative/releases).
2. Verify that the APK comes from this repository.
3. Install it over an existing compatible OpenNative build to retain app-private data.
4. Do not uninstall first when containers or saves must be preserved.

OpenNative has no third-party in-app updater. Before redistributing an APK, review [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES); bundled components may have licenses and redistribution terms separate from the application source.

## Adding a custom game

1. Extract the game into a folder Android can access.
2. Open **Library > Custom** and grant access through Android's folder picker.
3. Select the actual game executable in the container settings.
4. Begin with a conservative resolution, frame cap and compatibility-oriented translation profile.
5. Change one setting at a time and validate a repeatable scene.

The tested AYN Thor starting point for Gamma Emerald is documented in [Gamma Emerald](docs/GAMMA_EMERALD.md).

## Build from source

### Requirements

- JDK 17
- Android SDK 36
- Android NDK `27.3.13750724`

```bash
git clone https://github.com/RemiPelloux/OpenNative.git
cd OpenNative
./gradlew :app:assembleModernDebug
./gradlew :app:assembleModernRelease
```

Optional API keys belong in `local.properties` or environment variables. Never commit credentials, signing keys, games, saves or private diagnostic data.

## Testing

Run the smallest relevant test scope while developing:

```bash
./gradlew :app:testModernDebugUnitTest --tests 'app.gamenative.performance.*'
```

Run the full modern JVM suite only in an environment with sufficient memory and disk space:

```bash
./gradlew :app:testModernDebugUnitTest
```

Mockito and MockK use an explicit ByteBuddy agent for JDKs that restrict dynamic attachment. Generated Gradle outputs are disposable and can be removed with `./gradlew clean`. Performance changes must follow the [measurement protocol](docs/PERFORMANCE.md).

## Documentation

| Document | Purpose |
| --- | --- |
| [Roadmap](ROADMAP.md) | Milestones, delivery phases and release gates |
| [1.5.0 roadmap](docs/ROADMAP_1.5.0.md) | Detailed implementation and validation plan |
| [Provider tabs](docs/CUSTOM_PROVIDER_TABS.md) | Provider, AllDebrid, transfer and Installer Manager contract |
| [Provider feeds](docs/PROVIDER_FEEDS.md) | User-supplied JSON and RSS/Atom catalog contract |
| [Adaptive Engine](docs/ADAPTIVE_ENGINE.md) | Decision model, safeguards and resolution policy |
| [Performance method](docs/PERFORMANCE.md) | Benchmark protocol and current AYN Thor findings |
| [Independence](docs/INDEPENDENCE.md) | Compatibility identifiers, attribution and infrastructure migration |
| [Changelog](CHANGELOG.md) | Released behavior and compatibility changes |

## Security and privacy

- Provider credentials must remain in Keystore-backed local storage and are excluded from exports and diagnostics.
- OpenNative does not automatically upload gameplay telemetry or diagnostic reports.
- Provider features do not include a copyrighted-content index, DRM bypass or automatic execution of downloads.
- Destructive maintenance is scoped, explicit and delayed until verification completes.

Read the [privacy policy](PrivacyPolicy/README.md) before using online services. Review every diagnostic bundle before sharing it.

## Contributing

Focused fixes, tests, compatibility work and evidence-backed performance improvements are welcome. Reports should include the device, Android version, SoC/GPU, driver, game version, runtime configuration and reproducible steps, with private paths removed.

See [CONTRIBUTING.md](CONTRIBUTING.md) for development and validation requirements.

## Credits and license

OpenNative derives from GameNative by Utkarsh Dalal and its contributors and incorporates work from Wine, Proton, DXVK, VKD3D, Mesa, Box64, FEX and other open-source projects. Copyrights, license notices and Git history are preserved. Attribution does not imply shared governance or endorsement.

OpenNative source is licensed under [GPL-3.0](LICENSE). Bundled components may use different licenses; consult [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES).
