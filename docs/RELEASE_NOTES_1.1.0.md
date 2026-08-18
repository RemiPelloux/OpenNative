# OpenNative 1.1.0

OpenNative 1.1.0 completes the app's independent identity and removes background work that remained after inherited community services were disabled. It is an in-place Android ARM64 update: the application ID and signing identity remain unchanged so existing containers and saves stay attached to the installation.

## Improvements

- OpenNative branding, launcher assets, support links and privacy documentation now consistently point to the independent project.
- The library no longer starts compatibility, device-stat or GPU-stat jobs against unavailable services.
- Manual library refresh no longer clears and rebuilds those unavailable datasets.
- GOG recommendations keep their local/store ranking without a second unavailable compatibility/stat enrichment pass.
- New containers no longer block on a remote known-config request that cannot return a profile.
- Portable local profile import/export and component validation remain available.

## Migration safety

On the first 1.1.0 launch, OpenNative clears inherited compatibility/stat cache blobs and removes filters or sorts that depended on them. If those were the only selected filters, the library returns to the normal Games view. The migration does not alter containers, game installations, controller mappings, shader caches or saves.

## Verification

- Focused unit tests cover filter and sort migration.
- Existing configuration-parser tests verify that local profile import remains functional.
- The release APK is built for Android ARM64 and validated for package, version and signing identity before an in-place AYN Thor installation.

## Artifact

- `OpenNative-1.1.0-modern-arm64.apk`
- SHA-256: `5b93f339227c7cb616e7c1a127379f4b309c7dd49b156848e19e58406c2ab5fd`
- Package: `com.remipelloux.gamenativecustom`, version code 30, Android ARM64.

## Known limitations

- Compatibility and performance remain title, runtime, driver, cache and thermal-state specific.
- Remaining inherited component-download hosting is documented in `docs/INDEPENDENCE.md`; it is not silently replaced before archive licensing and digest parity are verified.
- The secondary-screen cockpit redesign is planned next: complete OpenNative branding, improved visual hierarchy and consistent touch/controller navigation.

OpenNative 1.1.0 does not claim a universal FPS increase. Report reproducible issues at <https://github.com/RemiPelloux/OpenNative/issues> without including credentials, saves or game files.
