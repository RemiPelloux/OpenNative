<div align="center">

<img src="docs/brand/opennative-mark.svg" alt="OpenNative logo" width="112" />

# OpenNative

**A performance-focused Windows compatibility environment for Android gaming handhelds.**

[![Release](https://img.shields.io/github/v/release/RemiPelloux/OpenNative?style=flat-square&logo=github)](https://github.com/RemiPelloux/OpenNative/releases)
[![Android](https://img.shields.io/badge/Android-ARM64-3DDC84?style=flat-square&logo=android&logoColor=white)](#platform-support)
[![License](https://img.shields.io/badge/license-GPL--3.0-36C5F0?style=flat-square)](LICENSE)
[![Sponsor](https://img.shields.io/badge/Sponsor-RemiPelloux-EA4AAA?style=flat-square&logo=githubsponsors&logoColor=white)](https://github.com/sponsors/RemiPelloux)

[Download](https://github.com/RemiPelloux/OpenNative/releases) · [Sponsor](https://github.com/sponsors/RemiPelloux) · [Roadmap](ROADMAP.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md)

</div>

OpenNative brings Wine/Proton, DXVK, VKD3D, Mesa, Box64 and FEX together in an Android interface designed for handheld gaming. The project prioritizes predictable frame delivery, low background overhead, controller reliability, portable configuration and safe runtime management.

OpenNative is an independent open-source project. It does not include games, firmware, store credentials or third-party shader caches. Use it only with software, downloads and accounts you are authorized to access.

## Project status

| Channel | Version | Status |
| --- | --- | --- |
| Stable | `1.5.0` | Live themes, handheld appearance picker and sampled download progress |
| Next milestone | `2.0.0` | Identity migration, modular runtimes and remaining `1.5.0` certification items |
| Architecture milestone | `2.0.0` | Long-term roadmap |

## Current capabilities

### Runtime and compatibility

- Unified Steam, Epic, GOG, Amazon and custom-executable library flows.
- User-created provider tabs after **Custom**, backed by a user-supplied HTTPS JSON or RSS/Atom feed. Launch seeds the bundled FitGirl and Skidrow tabs.
- Wine/Proton containers with Box64 and FEX translation options.
- Optional shared Wine prefix so a large library does not create one container per game.
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

## Shipped in 1.4.0

- New games share one Wine prefix by default (GameHub/Winlator style). Existing dedicated containers stay isolated.
- Each shared-prefix game keeps its own `A:` mapping and executable overlay.
- Toggle the mode under **Settings → Emulation**. See [shared Wine prefix](docs/SHARED_WINE_PREFIX.md).

## Shipped in 1.4.2

- AllDebrid device-wide keys now take precedence over stale per-tab credentials.
- Settings can select AllDebrid, Real-Debrid, Premiumize, Debrid-Link or TorBox, each with its own encrypted API key.
- OpenNative checks GitHub Releases once per day and verifies the downloaded APK package, version, signing certificate and published SHA-256 digest before opening Android's installer.
- Provider transfers use the current AllDebrid request contract and keep key validation responsive.
- Interrupted downloads recover safely when a host does not honor byte ranges.
- Resolver filenames are confined before staging, preventing path traversal.

## Shipped in 1.3.1

- Skidrow accepts verified ISO9660 images, extracts them into Custom, and removes the source image after successful registration.
- Turnip, FEX, Box64 and WOWBox64 catalogs include the latest integrated upstream releases.
- Wine container copying, Bionic verification, GOG v1 installation, portrait placement and shared-memory pacing carry current upstream fixes.
- Skidrow browses the live site archive, pages on scroll, and searches the whole catalog.
- Skidrow downloads unlock 1fichier through AllDebrid, extract into Custom, and delete the archive.
- The catalog header is compact, search hides on scroll, and covers show **Installed** when the title is already in Custom.
- Play launches the game exe (not the file manager), and Custom uses the catalog cover image.
- FitGirl keeps its WordPress catalog, AllDebrid unlock, and on-device pack install into Custom.

See [provider tabs](docs/CUSTOM_PROVIDER_TABS.md) and [feed contract](docs/PROVIDER_FEEDS.md).

## 1.5.0

- Themes now apply live: Light, Dark, AMOLED, Thor Ember, Ocean, Forest, Dusk and Slate, with Soft through Mono palettes.
- Settings opens with a visual appearance picker sized for handheld use.
- Download progress updates are sampled so the Downloads list stays responsive during large transfers.

Remaining certification items from the [1.5.0 roadmap](docs/ROADMAP_1.5.0.md) continue toward `2.0.0`: Wine setup sessions, settings sharing, cockpit polish and measured frame-delivery work.

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

1. Extract the game into a folder Android can access, or use a provider tab (FitGirl / Skidrow) to download and install into Custom.
2. Open **Library > Custom**. Provider installs register the folder automatically.
3. Tap **Play**. OpenNative picks the game `.exe` and ignores crash handlers, redistributables and setup tools. You can still open the container and choose another executable.
4. Begin with a conservative resolution, frame cap and compatibility-oriented translation profile.
5. Change one setting at a time and validate a repeatable scene.

Provider installs also copy the catalog/RSS artwork into the Custom folder as `cover.jpg` or `cover.png`. See [Installing games](docs/INSTALLING_GAMES.md) and [Gamma Emerald](docs/GAMMA_EMERALD.md).

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
| [Feature and emulation roadmap](docs/EMULATION_ROADMAP.md) | Planned runtime, graphics, translation, input, audio and recovery improvements |
| [Provider tabs](docs/CUSTOM_PROVIDER_TABS.md) | Provider, AllDebrid, transfer and Installer Manager contract |
| [Provider feeds](docs/PROVIDER_FEEDS.md) | User-supplied JSON and RSS/Atom catalog contract |
| [Installing games](docs/INSTALLING_GAMES.md) | FitGirl and Skidrow on-device install, Play exe and catalog covers |
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
