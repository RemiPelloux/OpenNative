# Changelog

## 1.5.0 - 2026-08-31

- Apply the selected theme and palette for real, including a working light mode and true AMOLED black.
- Add Thor Ember, Ocean, Forest, Dusk and Slate color themes, plus Soft, Vibrant, Bold, Neutral, Rainbow and Mono palettes.
- Put a visual appearance picker at the top of Settings so theme and palette can be changed on a handheld without a hidden dialog.
- Restyle settings tiles from the active color scheme so Light and AMOLED stay readable.
- Sample download progress at 150 ms so byte-level callbacks no longer rebuild the Downloads list on every tick.

## 1.4.2 - 2026-08-30

- Fix Skidrow encrypted ZIP installs by applying the password published in the source post, preserving the real extraction error, and deleting only failed job staging files.
- Keep the Custom destination private until extraction completes, then publish it atomically so failed downloads never leave an empty or partial game folder.
- Detect archive formats from their verified signature when a debrid host returns a RAR with a misleading ZIP filename, and preserve the latest persisted transfer state when reporting failures.
- Recursively unwrap up to four verified archive layers, retain multipart siblings during extraction, and support source-post passwords for encrypted ZIP, RAR and 7z payloads.
- Recover valid ZIP entries when Android or Zip4j reject a malformed central directory, using local-header streaming and a libarchive ZIP fallback, while rejecting incomplete downloads that have no ZIP end record.
- Keep GitHub release builds compiling when optional PostHog, SteamGridDB and Play Integrity secrets are unset.
- Fix AllDebrid keys saved in older per-tab entries overriding a newly configured device-wide key.
- Use the current AllDebrid POST request contract for link unlock, redirector and delayed-link polling.
- Trim credentials, verify encrypted readback and keep resolver validation off the UI thread.
- Recover resumable downloads when a host ignores `Range` and returns the full payload with HTTP 200.
- Reject unsafe resolver filenames before creating staging paths.
- Add selectable AllDebrid, Real-Debrid, Premiumize, Debrid-Link and TorBox accounts with separate encrypted credentials.
- Make the debrid service picker scrollable on short landscape displays.
- Add a daily GitHub release check, an update modal, bounded APK download, checksum/package/signature verification and Android installer handoff.

## 1.4.0 - 2026-08-30

- Add a shared Wine prefix so new games reuse one container, like GameHub and Winlator, instead of copying Proton for every title.
- Keep existing per-game containers isolated. Nothing is migrated automatically.
- Store each shared-prefix game’s executable in a local overlay and remap `A:` to that game folder at launch.
- Refuse to delete the shared prefix when one game is uninstalled.
- Add **Settings → Emulation → Shared Wine prefix** (on by default). Turn it off to create a dedicated prefix for the next new game.

## 1.3.1 - 2026-08-29

- Recognize verified ISO9660 Skidrow downloads and extract them natively with the same path, entry-count and expanded-size safeguards as RAR and 7z archives.
- Delete a Skidrow source archive only after extraction and install registration succeed, while retaining setup files until they produce a usable game installation.
- Update the upstream runtime catalog with current Turnip, FEX, Box64 and WOWBox64 releases.
- Fix Wine container file-copy crashes, Bionic verification recovery and GOG v1 installer registry/CD-drive setup.
- Add portrait top-aligned game display placement and exempt pipelined clients from shared-memory frame pacing.

## 1.3.0 - 2026-08-21

- Browse Skidrow from the live site archive instead of a thin RSS search, and keep paging as you scroll.
- Skip magnets on Skidrow, unlock 1fichier through AllDebrid, extract the archive into Custom, and delete the rar.
- Compact the Skidrow header: smaller title, icon refresh/delete, and a search field that hides while scrolling.
- Mark Skidrow covers with an Installed label when that title already exists in Custom.
- Launch the real game exe from Play even when a crash handler sits next to it.
- Copy the catalog cover into Custom as `cover.jpg` / `cover.png` when a provider game is installed.
- Keep FitGirl and Skidrow search on the whole catalog, not only the first page.

## 1.2.1 - 2026-08-20

- Seed a Skidrow provider tab from the FeedBurner RSS, unlock 1fichier through AllDebrid, extract the rar into Custom, and delete the archive.
- Lazy-load FitGirl and Skidrow catalog pages on scroll, and search the whole WordPress catalog instead of the first page.
- Fetch Skidrow through the site RSS (`s` + `feed=rss2`) so refresh no longer hits a 403 REST endpoint.
- Run Windows installers with Box64 and write every magnet file next to `setup.exe` in the title folder.
- Force Box64 on every Custom installer (`setup`/`install`/`.msi`) at container create and launch.
- Install every Windows pack into `D:\games\<slug>` (`Download/games`) and delete the FitGirl pack after a verified game exe exists.
- Document the no-PC FitGirl flow in `docs/INSTALLING_GAMES.md`.
- Seed `assets/opennative-provider-tabs.json` as the default provider tab on first launch.
- Show WordPress covers from public media, Jetpack/Yoast metadata, or the first HTTPS image in post content.
- Give provider tabs a library-style cover grid and a detail modal with Download and Install to Custom.
- Register finished portable installs in Custom and delete the installer only after a confirmed install.
- Keep FitGirl and other user feed hosts off the restored host denylist (`google.fr`, `facebook.fr`).
- Page WordPress REST catalogs at 20 posts so full `content` stays under the 2 MB fetch cap.
- Unlock file-hoster HTTPS links on Install instead of catalog or tag pages, retry the next host if unlock fails, and show the resolver error.

## 1.2.0 - 2026-08-20

- Add user-created provider tabs after Custom. Each tab is backed by a user-supplied HTTPS JSON envelope or optional RSS/Atom URL. OpenNative still ships no built-in catalog.
- Refresh the latest three feed pages on app open when a tab is set to daily refresh, and add a manual refresh-all action in Settings.
- Paginate user feeds with `page` / `per_page` / `orderby` / `order`, WordPress REST `X-WP-TotalPages`, and WordPress RSS `paged`.
- Add a provider-tab search field that filters locally and sends WordPress `search=` or RSS `s=`.
- Batch provider feed upserts into one Room transaction, combine catalog collectors on tab change, rate-limit transfer progress updates, and request only public WordPress REST fields.
- Store optional AllDebrid keys in a dedicated Keystore alias. The first download click can prompt for a key; dismiss is allowed and Download stays disabled until validation succeeds.
- Download selected links with Range resume, size/space reservation, SHA-256 verification, portable archive extraction, and installer deletion only after a committed install receipt.
- Bound feed refresh and AllDebrid resolve to one in-flight request each, keep job progress on keyed rows, and pause catalog work during an active game session.
- Export and import provider tabs as a versioned JSON bundle. AllDebrid keys stay in the device Keystore and a Settings key applies to every tab.
- Strip WordPress `_embed` and unused fields from user feed URLs so catalog fetches stay on public title, link, excerpt and media metadata.

## Unreleased

- Decode WordPress HTML entities such as `&#038;` in provider titles and game detail text.
- Open provider games with the same full-screen hero and install action as other library tabs.
- Parse FitGirl `3.6/4 GB` size lines and hide a size label when no size is known.
- Hide FitGirl Updates Digest posts from provider tabs.
- Load later provider catalog pages by scrolling instead of a Load next page button.

- Integrate GameNative upstream through `78bc4bbe`, retaining download I/O, service locale and LSFG 1.3.3 fixes while preserving OpenNative's independent product policy.
- Debounce download-resume metadata writes, use thread-safe progress listeners and use bounded FileChannel writes for Epic and GOG assembly on external storage.
- Load the LSFG native layer from Android's native library directory and discover `Lossless.dll` without creating a utility container or deleting any existing user container.
- Keep OpenNative free of upstream membership prompts, remote feedback, personalized community recommendations and upstream README branding.
- Make Mockito and MockK tests work on restricted JDKs through an explicit ByteBuddy agent, with bounded single-worker test execution.
- Fix locale-dependent HowLongToBeat hour formatting and update the Nexus client test for the OpenNative application header.
- Replace completed pre-1.1 roadmaps and duplicated release documents with focused `1.5.0` and `2.0.0` plans.
- Plan portable, redacted per-game and global settings sharing for `1.5.0`, with import previews and reversible conflict handling.
- Define `1.5.0` user-created provider tabs through a `+` after Custom, with optional secret-safe AllDebrid resolution and verified install cleanup.
- Specify a Wine Installer Manager for provider-downloaded `.exe` and `.msi` packages, including process-tree completion, executable discovery and guarded cleanup.
- Turn the `1.5.0` provider work into a gated delivery roadmap and publish its headline features without presenting them as shipped in `1.1.0`.
- Rewrite the public README and roadmap with professional release status, capability, delivery, acceptance and governance sections.
- Expand `1.5.0` optimization work around zero-allocation frame delivery, bounded streaming, request deduplication, paging and recomposition control.
- Simplify the main README and remove unused store API snapshots from the maintained documentation set.

## 1.1.0 - 2026-08-18

- Complete the independent OpenNative identity with the ON launcher icon, OpenNative support links, updated privacy documentation and no inherited updater, Discord, Ko-fi, feedback or optional community API flows.
- Stop disabled compatibility and community-stat jobs from scanning library pages, spawning coroutines and reranking recommendations in the background.
- Remove the non-functional remote "Use known config" action and its blocking container-creation lookup while preserving portable local profile import and export.
- Clear inherited compatibility, device-stat and GPU-stat caches once during migration. Obsolete remote filters and sorts are reset without modifying containers, game files or saves.
- Keep GOG's local recommendation experience while removing its dependency on unavailable compatibility and community-stat data.
- Add focused migration coverage and retain the existing local configuration parser test suite.
- Add a polished, fully OpenNative-branded secondary-screen cockpit redesign to the next roadmap milestone.

OpenNative 1.1.0 is a background-overhead and consistency release. It does not claim a universal FPS increase; game performance still depends on the title, runtime, driver, cache state and thermal conditions.

## 1.0.0 - 2026-08-16

- Add a bounded predictive shader warmup based only on files observed after the previous clean session.
- Reject stale, modified and path-escaping warmup entries; never download or import foreign shader caches.
- Scale shader read-ahead from 0 to 16 MiB using Android's current available-memory and low-memory state.
- Keep adaptive metrics in memory while disabling JSONL writes and periodic metric logs during normal play; persistent capture now requires diagnostics mode.
- Move the X server startup log out of the Compose recomposition path.
- Serialize shader snapshot, cleanup and health-state transitions at session boundaries without adding locks to frame delivery.
- Retain the release-candidate database batching, component integrity, shader snapshots and sustained memory-pressure governor.
- Add regression tests for warmup bounds, memory gating, changed files and managed-root confinement.

OpenNative 1.0.0 does not claim a universal FPS increase. Renderer, translation-runtime and game-specific gains still require controlled A/B captures on the same device, scene, driver and thermal state.

## 1.0.0-rc.1 - 2026-08-16

- Remove confirmed Steam, package, DLC, branch and Nexus collection N+1 query paths with chunked reads that respect SQLite's 999-parameter limit.
- Replace forced per-launch DXVK, graphics-driver and PulseAudio extraction with versioned integrity manifests and automatic repair of missing or empty critical files.
- Promote bundled components through a staged directory so a failed extraction does not destroy the previous working PulseAudio installation.
- Reuse a clean-session shader-cache statistics snapshot on the next launch, consume it before the guest starts and fall back to a real scan after crashes or cache changes.
- Add low-allocation swap and Linux PSI sampling on the existing slow resource cadence.
- Gate adaptive model training and shader maintenance with sustained memory-pressure hysteresis instead of reacting to one noisy sample.
- Share daemon process-output executors and disable unconditional guest stdout logging in release builds.
- Add focused tests for component repair, shader snapshot validation, SQLite batches above 999 IDs, swap/PSI parsing and memory-pressure transitions.

This is a release candidate. Performance uplift is not claimed until controlled A/B captures, 30 launch/stop cycles and the 60-minute AYN Thor soak pass.

## 0.3.0-beta.1 - 2026-08-16

- Add the guarded Adaptive Engine with observation-only mode as the default and explicit per-game opt-in for automatic resolution changes.
- Preserve the native resolution ceiling, apply staged changes only on the next launch and roll back unsuccessful probes.
- Classify sustained GPU, CPU, memory, thermal and frame-pacing pressure from session telemetry.
- Predict five-second p95 frametime and thermal trends with a bounded constant-state online model.
- Never lower resolution for CPU, memory or frame-pacing stalls and never change device clocks.
- Add Qualcomm/Adreno capability reporting, memory-pressure policy and Shader Health cache diagnostics.
- Add Adaptive Engine controls to the quick menu and AYN Thor secondary-screen cockpit.
- Export sanitized local diagnostics with paths and credentials redacted.
- Sample Android memory pressure on the existing slow resource cadence rather than in the frame path.
- Document the model, safeguards and remaining AYN Thor performance-certification work.

## 0.2.2 - 2026-08-16

- Add persistent per-container shader caches for DXVK, Mesa/Zink and VKD3D across Bionic and glibc launchers.
- Scope Mesa, DXVK and VKD3D generations independently so a driver update does not discard reusable DXVK state.
- Migrate the original unified cache layout by same-filesystem rename and report cold/warm cache growth for every session.
- Preserve explicit user cache paths and fall back safely if cache directories cannot be created.
- Add on-demand cache statistics and guarded cache-cleanup APIs without scanning during gameplay.

## 0.2.1 - 2026-08-16

- Fix the DXVK state cache path to use OpenNative's real application data directory.
- Apply a conservative automatic guest graphics-memory budget on 6-14 GB Android devices when a profile is still configured as unlimited.
- Reclaim SurfaceFlinger conversion buffers after transient game windows close.
- Return safely to the library when a stale launch route references a missing container.
- Document the Gamma Emerald battle-load findings and memory behavior on the 12 GB AYN Thor.

## 0.2.0 - 2026-08-16

- Make power-control affinity opt-in and respect native and WoW64 container CPU masks.
- Batch mod-profile database work and suppress duplicate custom-game icon extraction.
- Add measured Gamma Emerald CPU, thermal, memory and frame-pacing guidance.

## 0.1.0 - 2026-08-16

- Rebrand the user-facing application and documentation as OpenNative.
- Add asynchronous SurfaceFlinger compatibility conversion with safe buffer lifetime and shutdown draining.
- Add a dual-screen performance cockpit with session shortcuts and fallback behavior.
- Reduce hidden telemetry sampling and buffer JSONL session writes.
- Add portable, versioned container profile import/export.
- Batch GOG database reads and storage-path migrations to remove N+1 work.
- Provision only configured guest controller slots and gate evshim diagnostics behind `EVSHIM_DEBUG`.
- Preserve the existing application ID and storage directory for in-place test-device upgrades.
- Disable the upstream self-updater for fork builds.
