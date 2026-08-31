# OpenNative Feature and Emulation Roadmap

## Vision

OpenNative should make Windows games on Android handhelds feel understandable and recoverable. A player should be able to install a game, see the complete compatibility stack, choose a tested profile, launch with controller-first defaults and recover from a bad runtime or setting without rebuilding everything.

OpenNative is not a single emulator. It coordinates several layers:

1. **Windows compatibility:** Wine or Proton implements Windows APIs.
2. **CPU translation:** Box64, FEX and WoW64/ARM64EC paths execute x86/x64 code on ARM64.
3. **Graphics translation:** DXVK and VKD3D-Proton translate DirectX to Vulkan; WineD3D and Zink provide fallback paths for other workloads.
4. **Android presentation:** the renderer, SurfaceFlinger integration, scaling and frame pacing present the guest image on the device.
5. **Platform services:** audio, input, storage, networking, stores, installers and saves complete the experience.

Improving compatibility means treating these layers as a tested system. Updating one component without checking its contracts can make a previously working game slower or unplayable.

## Status model

| Status | Delivery meaning |
| --- | --- |
| **Committed: 1.5** | Daily-driver work accepted for `1.5.0`; it must fit the current architecture |
| **Committed: 1.6** | Wine, container and launch-path work accepted for `1.6.0`; no identity migration |
| **Committed: 2.0** | Architecture required for the `2.0.0` modular-runtime milestone |
| **Candidate: 2.x** | Prioritized feature that needs a design, prototype and measured maintenance cost before assignment |
| **Research** | Exploration only; no release promise |

Every promoted feature requires a rollback path, supported-device scope, automated coverage and player-facing limitations.

## Player experience targets

| Target | Player outcome | Success measure |
| --- | --- | --- |
| First launch | A new game reaches a useful result with fewer blind configuration changes | Preflight explains blockers; recommended profiles are reversible |
| Daily play | Launch, controller, audio and display behavior remain stable across repeated sessions | 100 launch/stop cycles and multi-hour device soaks pass |
| Experimentation | Runtime changes cannot destroy a known-good setup | Immutable components, profile history and one-action rollback |
| Performance | Changes improve smoothness or efficiency, not only a synthetic score | Repeatable FPS, p95/p99, power, memory and thermal evidence |
| Recovery | A failed install, migration or launch leaves actionable recovery material | No unverified deletion; interrupted operations resume or roll back |
| Portability | Useful settings can move between devices without leaking private data | Redacted, versioned, diffable profiles round-trip correctly |

## 1. Compatibility Doctor and one-tap recovery

**Status: Committed: 1.5**

### Features

- A launch timeline covering preflight, prefix preparation, runtime activation, guest process start, graphics initialization, first frame and clean exit.
- Failure categories for missing files, incompatible runtime combinations, prefix problems, translator faults, graphics initialization, audio startup, input loss, low space and Android process death.
- A temporary **safe launch** mode that disables optional overrides, frame generation and experimental layers without modifying the saved profile.
- A last-known-good launch record per title, including component versions and relevant settings.
- Scoped repair for the failing component or prefix metadata instead of a broad reset.
- Sanitized support bundles with secrets, account identifiers, private paths and game content removed.

### Promotion gate

- Known fixture failures always produce the same category and an applicable action.
- Safe launch and repair are reversible and never delete a working prefix or game.
- Diagnosis performs no network upload and adds no continuous gameplay logging.

## 2. Modular Runtime Lab

**Status: Committed: 2.0**

### Features

- Install multiple immutable Wine and Proton versions side by side.
- Install and update Box64, FEX, Mesa/Turnip, DXVK, VKD3D-Proton and supported helper layers independently.
- Display a per-game runtime lockfile with exact versions, digests, provenance and compatibility state.
- Show component disk usage, games that depend on it and whether a rollback version is available.
- Activate a new stack transactionally: download, verify, extract, smoke-test, then switch the game profile.
- Keep the last known-good stack until the replacement completes a successful launch.
- Compare two profiles using the same local benchmark capture without changing global defaults.

### Qualification

Every promoted stack must pass:

- 32-bit and 64-bit process creation where declared supported;
- registry, filesystem, locale and common redistributable probes;
- XInput and audio initialization;
- DirectX 9/11/12 and Vulkan smoke scenes appropriate to the selected wrappers;
- fresh-prefix, upgraded-prefix, component rollback and offline relaunch tests.

## 3. Wine, Proton and prefix evolution

**Status: Committed: 1.6 warm-start and hygiene; Committed: 2.0 foundation; Candidate: 2.x features**

### 1.6 warm-start improvements

- Persist a prefix generation marker (Wine/Proton id, wincomponents, wrapper digest, locale, clean shutdown) and skip `wineboot` when it matches.
- Copy DX wrappers, wincomponents and original DLLs only when the on-disk digest differs.
- Install Wine Mono and Gecko once per prefix; never re-run their MSI on a warm launch.
- Patch `system.reg` / `user.reg` for changed keys only.
- Default play launches to essential Wine services; keep a Compatibility preset that still runs full wineboot and helper services.
- Keep `WINEDEBUG` off on the play path. Doctor sessions may enable bounded channels and must turn them off afterwards.
- Measure wineserver start, ready, idle RSS and stop as first-class launch-timeline stages.
- Do not run `wineserver -k` between sequential pre-install or interface-generation commands unless the prefix or architecture changed.
- Keep `WINEESYNC=1` as the default. Offer FSYNC or NTSYNC only as a per-title measured option with one-action rollback.
- Show prefix storage by Windows, users, temp, crash dumps and caches. **Trim prefix** is opt-in, previewable and cannot touch saves or `A:` game files.
- Persist font/glyph caches across launches; rebuild them only after Wine or locale changes.

### 2.0 and later planned improvements

- Separate prefix identity from runtime identity so a runtime test does not silently migrate every container.
- Add prefix snapshots before risky runtime, registry or redistributable changes, with size estimates and explicit retention rules.
- Detect common dependencies and offer reviewed installation of required redistributables rather than running arbitrary scripts.
- Track installer child processes, reboot requests and filesystem quiescence so setup completion is not inferred from one process exiting.
- Improve Windows path, drive, case-sensitivity, locale and removable-storage diagnostics.
- Add per-title DLL override groups with an explanation of their source and rollback behavior.
- Build compatibility probes for launchers, media frameworks, .NET, Visual C++ and common game middleware.

### Candidate player features

- **Launch profile:** Fast boot, Compatibility and Safe, explained before launch.
- **Runtime channels:** Stable, Compatibility and Experimental views with clear support boundaries.
- **Prefix history:** See what changed between the last working and current launch.
- **Clone for testing:** Create a space-aware copy or snapshot of a prefix before experimenting.
- **Clean-room launch:** Test a game in a temporary prefix without replacing the main container.
- **Reviewed redistributable packs:** VC++, .NET, XNA, Media Foundation and PhysX with preview and rollback.
- **Virtual desktop and DPI:** Optional Wine virtual desktop and per-title DPI for games that mis-handle Android window size.

### Promotion gate

No feature may auto-modify an existing prefix without a preview, backup/rollback path and a fixture proving interrupted recovery. A warm-start skip is rejected if it hides a required wineboot after a Wine or wincomponent change.

## 4. CPU translation: Box64, FEX and ARM64EC

**Status: Candidate: 2.x; ARM64EC expansion is Research**

### Planned improvements

- Record translator startup time, code-cache growth, invalidation, hot translated threads and synchronization pressure in bounded local session summaries.
- Build workload profiles for startup-heavy, shader-heavy, CPU-bound, memory-bound and mixed 32/64-bit games.
- Make CPU affinity capability-based and per title; respect user masks and avoid forcing device clocks or global scheduler changes.
- Preserve translator caches by compatible runtime generation and invalidate them only when the ABI or translator format requires it.
- Test Box64 and FEX against the same repeatable scenes before recommending either one.
- Improve WoW64 coverage for games and installers that mix 32-bit helpers with 64-bit executables.
- Detect unsupported instructions or translator crashes and include the responsible process in the Compatibility Doctor report.

### Research directions

- Broader ARM64EC execution for compatible Proton/Wine paths.
- Per-process translator selection for launchers and helper processes, only if process ownership and shared-prefix behavior can be made deterministic.
- Profile-guided next-launch translator recommendations based on local evidence from the same game and device.

### Promotion gate

A recommendation must improve throughput, p95/p99 frametime, startup time or sustained power in alternating A/B captures. It must never switch translators during a running game and must retain the prior configuration.

## 5. DirectX, Vulkan and OpenGL compatibility

**Status: Committed: 2.0 routing; Candidate: 2.x backend expansion**

### Explicit graphics paths

| Guest workload | Primary direction | Fallback or research direction |
| --- | --- | --- |
| DirectX 8 | Qualified D3D8-to-Vulkan or D3D8-to-D3D9 path | WineD3D for titles with translation defects |
| DirectX 9 | DXVK with per-title version pinning | WineD3D where Vulkan behavior is incorrect |
| DirectX 10/11 | DXVK with capability and extension checks | Version rollback and WineD3D diagnostic fallback |
| DirectX 12 | VKD3D-Proton with feature-level reporting | Clear unsupported-feature diagnosis rather than blind retries |
| OpenGL | Native Wine OpenGL or Zink based on measured compatibility | Per-title fallback with extension reporting |
| Vulkan | Direct guest Vulkan path with driver qualification | Driver rollback and extension diagnostics |

### Features

- A graphics preflight page showing API, wrapper, driver, required Vulkan extensions, memory budget and known incompatibilities.
- Side-by-side DXVK/VKD3D version testing without replacing the globally installed version.
- Per-title renderer fallback after a failed initialization, offered to the user rather than applied silently.
- Visual-conformance captures for color, alpha, depth, texture compression, video output, resize, fullscreen and device loss.
- Explicit VRAM-budget guidance derived from Android memory pressure and the selected device, while preserving manual values.
- Crash-safe, backend-specific shader and pipeline generations with a visible invalidation reason.
- Driver qualification for Turnip/Mesa releases and a last-known-good driver per GPU family.

### Promotion gate

Supported paths must pass smoke and visual-conformance scenes, five-run performance captures and repeated device-loss/relaunch tests. A faster path is rejected if it introduces rendering corruption.

## 6. Frame pacing, scaling and frame generation

**Status: Committed: 1.5 measurement; Candidate: 2.x features**

### Planned improvements

- Trace guest presentation, wrapper queueing, Android buffer conversion, SurfaceFlinger submission and display presentation as one timeline.
- Remove steady-state allocations and redundant BGRA/RGBA copies only after buffer ownership is proven.
- Bound presentation queues and discard obsolete frames before expensive conversion when the producer outruns the display.
- Add per-title FIFO/mailbox and frame-cap recommendations based on evidence, without forcing a global present mode.
- Harden LSFG discovery, activation, resize, focus loss, frame pacing and clean fallback.
- Clearly show rendered FPS and displayed/generated FPS as separate values.
- Add integer scaling and aspect-correct scaling for pixel-art and fixed-resolution games.
- Evaluate an optional spatial upscaler with resolution scale and sharpness controls.
- Improve refresh-rate matching and external-display selection for handheld, docked and secondary-screen play.

### Research directions

- HDR and wide-color output across Wine, wrapper, Android surface and display color-management layers.
- Variable refresh rate where Android, the panel and presentation backend expose a reliable contract.
- Latency-aware frame-generation policy that disables itself when base FPS or pacing is unsuitable.

### Promotion gate

Frame features require frame-time traces, input-latency measurements, resize/focus tests and honest on-screen metrics. Generated frames are never reported as native rendered performance.

## 7. Shader and pipeline experience

**Status: Committed: 1.5 stability; Candidate: 2.x tooling**

### Features

- A per-game Shader Health page showing DXVK, Mesa/Zink and VKD3D cache generations independently.
- Cold-versus-warm launch comparison with shader events and p95/p99 frametime.
- Cache size budgets and post-session cleanup that never scans or deletes during gameplay.
- Format-aware invalidation when the relevant wrapper or driver changes, without discarding unrelated cache generations.
- Crash-safe journals so interrupted writes cannot make the next launch trust a partial generation.
- A user-visible explanation when a launch is expected to stutter because a compatible cache is cold.

### Boundary

OpenNative will not download third-party shader caches. Shader data remains local to the user's games and device.

## 8. Input and handheld controls

**Status: Committed: 1.5 reliability; Candidate: 2.x features**

### Planned improvements

- Qualify XInput, DirectInput, Raw Input and SDL controller paths with hot-plug and reconnect tests.
- Preserve stable player ordering for multiple controllers and expose conflicts before launch.
- Add per-game controller profiles with import preview and rollback.
- Improve rumble duration/intensity translation, trigger handling, dead zones and analog calibration.
- Add gyro-to-mouse and gyro-to-stick profiles with a physical enable gesture and visible active state.
- Expand touch controls with radial menus, action layers, hold/toggle modes and orientation-specific layouts.
- Make keyboard, mouse, controller and touch coexist without losing focus when a dialog or second display appears.
- Add a docked mode for external controllers, keyboard/mouse and TV-safe UI scaling.

### Promotion gate

Every input feature must pass reconnect, suspend, rotation, dialog, multi-controller and 30-minute hold/rumble tests without stuck input or reassignment.

## 9. Audio compatibility and latency

**Status: Candidate: 2.x**

### Planned improvements

- Instrument underruns, buffer fill, device-route changes and guest/host sample-rate conversion.
- Qualify XAudio2/FAudio, OpenAL, DirectSound and common middleware initialization paths.
- Add conservative **Stable**, **Balanced** and **Low latency** per-game audio profiles based on measured device capability.
- Recover after Bluetooth, USB, HDMI or built-in speaker route changes without restarting the whole application where possible.
- Keep voice input and microphone permission off unless the user enables a title that requires it.
- Diagnose crackle caused by CPU saturation separately from buffer-size or sample-rate mismatch.

### Promotion gate

Profiles require measured round-trip/estimated output latency, underrun counts and a sustained CPU-load test. Low-latency mode cannot become the default if it increases underruns.

## 10. Storage, installers and game data

**Status: Committed: 1.5 recovery; Candidate: 2.x expansion**

### Planned improvements

- Resume downloads, verification, extraction and copy stages independently after interruption.
- Reserve space for compressed input, expanded output, prefix growth and rollback before starting an install.
- Detect Android FUSE/SAF limitations and explain when an installer needs a managed staging path.
- Add transaction-like promotion so a failed update preserves the prior game directory.
- Retain installer packs until a playable executable and install receipt are committed.
- Add prefix snapshots and save-aware backups with previews, retention limits and restore tests.
- Detect moved or disconnected external storage and offer relink rather than destructive re-import.
- Deduplicate immutable runtime components safely; never deduplicate mutable prefixes, saves or game files by assumption.

### Large-library container strategy

The current architecture gives each game a dedicated Wine prefix. This costs more storage, but it prevents one game's registry changes, DLL overrides or redistributables from breaking another game. OpenNative will preserve that isolation as the default while adding two storage-conscious options.

#### Compact isolated containers

**Status: Committed: 2.0**

- Inventory exactly which bytes are duplicated across containers before selecting a deduplication mechanism.
- Move signed, immutable Wine/runtime/component payloads into a content-addressed store and reference them from each container.
- Keep each game's mutable Wine prefix, registry, users, saves and configuration private.
- Maintain reference counts and verify every dependent container before garbage-collecting an unused component.
- Materialize a private copy before any operation that would mutate shared content.

This mode should deliver most safe storage savings without creating cross-game compatibility failures.

#### Container groups

**Status: Shipped baseline: 1.4.0 shared prefix; Candidate: 2.x named groups**

- Let the user create named groups such as **General 64-bit**, **Legacy 32-bit** or **Visual novels**, then explicitly assign compatible games.
- Share one mutable Wine prefix within the group while retaining per-game executable, drive mapping, graphics, translator, display, input, environment and performance overlays.
- Allow only one active game or prefix-mutating installer in a group at a time.
- Snapshot the group before runtime, redistributable, registry or installer changes and show which games may be affected.
- Track installed dependencies and their owning games so cleanup cannot remove a shared dependency blindly.
- Provide **Move to dedicated container** and **Clone group for testing** actions with verified, resumable copy and rollback.
- Calculate the estimated storage saving before migration and never move existing games automatically.

Container groups trade isolation for space efficiency. They are useful for large libraries with compatible requirements, but they cannot be the universal default because games may need conflicting Wine versions, DLL overrides, registry state or redistributables.

### Promotion gate

- A 100-game fixture demonstrates bounded metadata and launch overhead.
- Grouped games retain independent launch settings and drive mappings across relaunch.
- Concurrent launches/installers cannot mutate the same prefix.
- A deliberately incompatible dependency change is detected or recoverable from the group snapshot.
- Dedicated-to-group and group-to-dedicated migration survive cancellation, low space and process death without losing the original container.

### Candidate player features

- **Install queue:** download now, install later while plugged in.
- **Storage planner:** show download, expanded game, prefix and rollback requirements before starting.
- **Repair game:** verify a provider manifest or store-owned files without resetting the prefix or saves.
- **Move game:** resumable verified transfer between internal and external storage.

## 11. Library, stores and cloud saves

**Status: Candidate: 2.x**

### Planned improvements

- Unify Steam, GOG, Epic, Amazon, Custom and provider games around one launch/profile/diagnostic contract without erasing store-specific behavior.
- Add duplicate-title grouping while keeping each installation and store entitlement distinct.
- Improve executable selection using bounded scoring, prior successful launches and explicit user choice.
- Add per-game launch history, last working profile and component-change markers.
- Make cloud-save conflict handling explicit: local, remote, newest or keep both, with timestamps and backup before resolution.
- Keep offline mode predictable and avoid background store/catalog work during an active game.
- Surface achievements, playtime and artwork only when the backing store supports them and the user is authenticated.

### Boundary

OpenNative will not claim compatibility with kernel anti-cheat or platform DRM that the Android/Wine environment cannot satisfy. Unsupported protection must be reported clearly rather than disguised as a runtime failure.

## 12. Compatibility profiles and community knowledge

**Status: Committed: 2.0 foundation; Candidate: 2.x service**

### Features

- A signed, versioned per-title profile format with game build, device/GPU scope, component requirements, provenance and confidence.
- A complete diff before applying a profile, grouped by runtime, graphics, display, input, audio and performance.
- Local overrides stored separately and never overwritten silently by catalog updates.
- Rollback history for every accepted profile change.
- Evidence labels such as **Maintainer tested**, **Community tested**, **Experimental** and **Outdated for this game build**.
- Offline retention of the last verified catalog snapshot.
- Export through Android sharing without accounts, credentials, paths, saves, binaries or shader caches.

### Promotion gate

Profiles must be signed, deterministic and reversible. Recommendations without reproducible device/game evidence cannot be marked stable.

## 13. Device and form-factor expansion

**Status: Candidate: 2.x**

### Planned scope

- Qualcomm/Adreno handhelds remain the primary qualification target.
- Add explicit Mali validation and at least one additional Android ARM64 GPU family.
- Build capability reports from extensions, memory, Android API and display behavior rather than model-name allowlists.
- Support handheld, phone, tablet, TV/docked and secondary-display layouts from one state model.
- Add thermal/power profiles only as recommendations backed by measurements; never force clocks, fan firmware or global Android changes.
- Validate cutouts, portrait displays, unusual aspect ratios, high-refresh panels and display hot-plug.

## 14. Container boot, ImageFs and shared-prefix activate

**Status: Committed: 1.6**

The current activate path replaces `home/xuser` with a symlink, may extract ImageFs extras, then starts the guest. That work is correct but often repeats when nothing changed.

### Features

- Instrument ImageFs validity, container activate, drive map, wineserver ready and first frame on the Compatibility Doctor timeline.
- Validate ImageFs with a generation marker instead of walking the rootfs on every launch.
- Extract graphics-driver and extras archives incrementally; skip payloads whose digest is already present.
- Make container activate transactional so a crash cannot leave `home/xuser` missing.
- Index container configs by id and mtime. Do not rescan every `xuser-*` directory from Compose recomposition or settings open.
- Remap shared-prefix `A:` without deleting the symlink when the same prefix is already active.
- Hold a prefix lock for `SHARED_PREFIX` so two games or installers cannot mutate it concurrently.
- After an installer dirties a shared prefix, offer snapshot or **Move to dedicated container** before the next unrelated game launches.
- Start `explorer.exe` and desktop theme only for installer or Compatibility presets.
- Load `lsteamclient` and the Steam pipe only for Steam titles, or when the user enables Steam API helpers on a custom game.

### Promotion gate

- Warm second launch: no ImageFs extract, no wineboot, no wrapper copy.
- Shared-prefix A then B: drive remap only; wineserver stays up unless the runtime changed.
- Activate-kill fixtures restore the previous container pointer.
- Custom non-Steam launches create no Steam client DLL.

## 15. Guest I/O, filesystems and Wine networking

**Status: Committed: 1.6 diagnostics; Candidate: 2.x features**

### Planned improvements

- Diagnose FUSE/SAF, FAT case-folding and missing drive letters before the guest reports a generic path error.
- Stream prefix clones, snapshots and large guest copies with bounded buffers; never hold a whole prefix in memory.
- Detect disconnected external storage and identify the affected drive letter, game folder or component.
- Keep Wine DNS and HTTP proxies explicit and per title; do not inherit surprising Android HTTP proxies into every guest.
- Surface winsock bind failures separately from translator crashes.

### Candidate player features

- **Storage planner for prefixes:** show current size, projected clone size and required free space.
- **Relink drive:** point `A:` or `D:` at a moved folder without recreating the prefix.
- **Multi-volume mount:** attach extra ISOs or installer parts as drive letters for the session only.

### Promotion gate

A failed remount or clone must leave the previous drive map and prefix intact. Networking changes cannot leak host credentials into the guest environment dump.

## 16. XServer, windowing and guest desktop cost

**Status: Committed: 1.6 reduce idle cost; Candidate: 2.x window policy**

### Planned improvements

- Skip wallpaper, desktop icons and unused Wine shell services on Fast boot game launches.
- Reduce X11 round-trips for cursor, focus and fullscreen transitions that currently hop through the UI thread.
- Keep the existing rule: do not resize a live XServer from Adaptive Engine; resolution changes apply on the next launch.
- Distinguish guest-presented frames from Android-displayed frames in the HUD when LSFG is active.

### Candidate player features

- Per-title virtual desktop size independent of the Android surface.
- External-display window policy that does not recreate the container.
- Optional borderless fullscreen hint for games that open a broken exclusive mode.

### Promotion gate

Desktop-skip must not break installer UIs. Window-policy changes require resize, focus-loss and secondary-display fixtures.

## 17. Research lab

These ideas are intentionally outside committed milestones until prototypes prove that they are maintainable and safe:

- Broader ARM64EC-native execution and mixed x86/x64 helper compatibility.
- Per-process CPU translator routing.
- HDR and wide-color output.
- Reliable variable refresh rate control.
- Latency-aware frame generation and spatial upscaling combinations.
- Lightweight session restoration. This means restoring launch state and UI, not promising a RAM/process snapshot that Android and Wine cannot safely resume.
- Reproducible automated game-startup probes for user-owned test libraries.
- Local profile recommendation models trained only from bounded data on the same device.
- Persistent wineserver across OpenNative activity recreation, only if prefix locks and GPU reset remain deterministic.
- OverlayFS or reflink clones for prefixes on filesystems that guarantee copy-on-write safety.
- In-kernel NTSYNC or futex-based Wine sync when Android and the Wine build expose a supported contract.
- Pre-warm the shared prefix while charging and idle, cancelled immediately when the user launches a game.

Research is rejected if it requires DRM bypass, unsafe host changes, downloaded shader caches, undisclosed telemetry, unreviewable binary sources or a device-specific hack enabled for everyone.

## Delivery order

1. Finish remaining `1.5.0` measurement: Thor soak, Perfetto presentation traces and honest FPS evidence.
2. Land `1.6.0` warm-prefix, wineserver, ImageFs activate, shared-prefix remap and prefix trim on the current architecture.
3. Land `2.0.0` identity migration and immutable modular-component contracts.
4. Qualify a small runtime matrix before expanding the number of downloadable versions.
5. Add Compatibility Doctor data to every layer before automatic recommendations.
6. Promote graphics, CPU, frame, input and audio improvements one at a time with A/B evidence.
7. Expand device coverage only after the generic capability model passes on the primary handheld.

## Release evidence

No emulation or performance feature is complete until the release evidence includes:

- exact app, game, runtime, translator, wrapper and driver versions;
- device, Android version, resolution, refresh rate and thermal starting state;
- five alternating A/B captures for performance-sensitive changes;
- median FPS, p95/p99 frametime, memory, swap, temperature and relevant layer-specific counters;
- cold and warm behavior where shaders or translation caches are involved;
- visual, audio and input correctness;
- launch/stop, suspend/reconnect and rollback tests;
- published limitations and a recovery path.

The measurement thresholds remain defined in [PERFORMANCE.md](PERFORMANCE.md).
