# OpenNative 2.0.0 Roadmap

## Goal

`2.0.0` is a **major product**, not a larger `1.8.0`. It changes what OpenNative is: its identity on the device, how runtimes are installed, how frames reach the panel, how two devices talk, and how compatibility knowledge moves — without shipping games, secrets or shader caches.

`1.6.0`–`1.8.0` must stay on the current package and ImageFs. `2.0.0` begins after `1.5.0` certification. `1.6`–`1.8` work may land first; it is not a substitute for the features below.

A `2.0.0` APK that only migrates storage or only adds another settings screen is not `2.0.0`.

## Major features (required)

These eight features are the release. Each has its own exit criteria. Shipping five of them and calling the build `2.0.0` is not allowed.

| # | Feature | Player-visible change |
| --- | --- | --- |
| 1 | **OpenNative Identity** | New application id and storage layout, resumable migration, old install left intact until verify |
| 2 | **Runtime Fabric** | Install, pin and roll back Wine/Proton, Box64/FEX, Mesa/Turnip, DXVK/VKD3D as signed, side-by-side packages |
| 3 | **Presentation Engine 2.0** | Vulkan scanout path that can retire `sfCompat` BGRA conversion when traces prove ownership; integer scale and optional spatial upscale as engine stages |
| 4 | **Compatibility Exchange** | Signed per-title catalogs with provenance, device scope and local-override protection; optional peer import over a share sheet or LAN |
| 5 | **OpenNative Link** | First-party companion on the same LAN: library, queue, recipe push, sanitized Doctor pull — no account required |
| 6 | **Console Shell** | Docked living-room mode: TV-safe UI, handheld as a controller, game stays on the big surface |
| 7 | **Save Vault** | Versioned, prefix-independent save store with timeline, export and restore |
| 8 | **Launch Protocol + Adapter SDK** | Documented intent/lockfile API for other apps; signed in-process provider plugins |

Supporting work (Device Family Cert, Cert Lab, local optimizer) is required for release quality but is not a substitute for any row above.

## 1. OpenNative Identity

- Final application id, public storage roots and content-provider authorities.
- Resumable inventory → hash → stage → atomic promote of containers, vault, recipes, caches.
- Never auto-delete the source tree. Verification report and rollback.
- Schema versions from every supported `1.x` release.
- Attribution, notices and git history stay.

Exit: interrupted, low-space and corrupt migrations recover; old and new builds are distinguishable; no save or container is orphaned.

## 2. Runtime Fabric

A store for **components OpenNative is allowed to redistribute**, not for games.

- Signed manifests: version, license, ABI, GPU scope, digest, compatible peers.
- Side-by-side immutable trees. One title’s pin cannot mutate another title’s stack.
- Lockfile per game: Wine/Proton, translator, driver, wrapper, audio, presentation extras.
- Download ≠ activate. Activate after integrity, load and smoke.
- One-action rollback to the last lockfile that produced a successful launch.
- Runtime inspector: provenance, disk, dependents, rollback availability.
- OpenNative-operated mirror only for components with verified rights ([INDEPENDENCE.md](INDEPENDENCE.md)).

### Compact layers

Reuse the `1.6.0` layer model. Sealed Wine/wincomponents/wraps live in the content-addressed fabric. Mutable prefixes stay private or grouped by explicit tier.

### Qualification matrix

32/64-bit probes, registry, filesystem, audio, XInput, DX/Vulkan smoke, fresh/upgrade/rollback prefix. Unpublished combinations stay **experimental**.

Exit: tamper-evident manifests; independent upgrade/rollback; smoke on the declared GPU family; compact containers do not rewrite a foreign prefix.

## 3. Presentation Engine 2.0

This is the engine major, not another HUD tweak.

- Split **guest render**, **convert**, **scanout** and **display** behind ownership tests.
- Ship a Vulkan (or proven zero-copy) scanout that can become the default when Perfetto shows BGRA `sfCompat` is no longer required for that device/driver.
- Keep `sfCompat` as a fallback path with an honest label.
- Integer scaling and optional spatial upscale as ordered stages (resolution, sharpness, aspect, latency).
- Crash-safe shader journals; format-aware invalidation; no frame-path walks.
- LSFG remains optional. Rendered vs displayed FPS stay separate.
- HDR / VRR stay research unless a device contract is proven.

Exit: traces for both paths; default scanout wins [PERFORMANCE.md](PERFORMANCE.md) without visual corruption; fallback still launches. No FPS claim without five alternating A/B runs.

## 4. Compatibility Exchange

- Versioned profile objects: game build, device/GPU scope, lockfile, evidence label (`Maintainer` `Community` `Experimental` `Outdated`).
- Apply only after a grouped diff. Local overrides never overwritten.
- Rollback history for every accepted catalog change.
- Offline snapshot of the last verified catalog.
- Import from Android share or Link. Export strips paths, accounts, saves, binaries, shader caches.
- Optional signed catalog feed that OpenNative publishes. Peers can share files without an OpenNative account.
- No gameplay telemetry by default.

Exit: signed, deterministic, reversible. A profile without device/game evidence cannot be marked stable.

## 5. OpenNative Link

A major surface that Winlator-class apps do not have as a first-party product.

- Companion (desktop or second Android) discovers the handheld on the LAN with a one-time pairing code.
- Capabilities: browse library, start/stop a queued install, push a recipe, pull a sanitized Doctor bundle, watch job stage (not a live game stream).
- Pairing keys in Android Keystore. TLS. No store credentials leave the device.
- Works offline after pairing. No OpenNative login.
- Refuse WAN relay, “cloud remote play” and unsolicited inbound control.

Exit: pairing fixtures; revoked companion cannot call; exports match the existing redaction rules; killing Link cannot kill an active guest.

## 6. Console Shell

- One navigation state for handheld, phone, tablet, TV/docked and the existing secondary cockpit.
- **Living-room mode:** game on HDMI/internal big surface; handheld or a paired controller drives a TV-safe launcher.
- Focus, scale and safe-area for 10-foot UI.
- Unplug dock: game continues on the panel; shell follows.
- Multi-user Android profiles stay isolated; OpenNative does not merge libraries across users.

Exit: dock plug/unplug and display-loss matrices. Localization of every new shell string.

## 7. Save Vault

Saves are a product, not a side effect of a Wine prefix.

- Detect and register save roots (Windows known folders, Steam userdata, user override).
- Timeline of snapshots with size, game build and recipe hash.
- Restore, export, import with preview. Default set excludes `A:` game files and `windows/`.
- Vault lives outside the mutable prefix so evacuate / fabric rollback cannot drop saves.
- Optional Keystore-wrapped archive. No automatic cloud upload.

Exit: prefix trim, slot-B flip and identity migration cannot delete vault objects. Conflict with store cloud-save uses the `1.8.0` dialog plus a vault snapshot.

## 8. Launch Protocol and Adapter SDK

- Public intent / document contract: launch by installation id or recipe hash, return Doctor correlation id.
- Other apps (launchers, voice, Tasker-class tools) may start a game they did not install only if the user granted that install.
- **Adapter SDK:** signed plugin package that implements the existing provider catalog/resolver interfaces.
- Plugins cannot receive filesystem, Wine or cleanup authority. They return metadata and user-selected links.
- Disable one plugin without blocking others. Last valid snapshot stays.

Exit: a sample plugin built in CI against fake feeds. A malicious path in plugin JSON cannot escape staging. Intents cannot launch an install the caller does not own.

## 9. Supporting majors (required for certification)

### Device Family Cert

- Qualcomm/Adreno remains primary.
- Mali certified with the same capability document, not a rename of Adreno defaults.
- One additional Android ARM64 GPU family smoked and documented.
- Thermal/power recommendations only; no forced clocks.

### Cert Lab

- In-app, user-owned: smoke scenes, five-run capture, visual stills, sanitized share.
- Uses only games and files the user already has.
- Labels **pass / fail / inconclusive**. Never a marketing FPS number without the protocol.

### Local optimizer

- Same-device session summaries only.
- Next-launch resolution or lockfile experiment with confidence, cooldown and rollback.
- Explainable from the memory waterfall and pacing pressure.

## Release certification

`2.0.0` ships only when **all eight major features** meet their exit criteria and:

- Reproducible signed builds, checksums, SBOM, component-mirror provenance.
- Migration tests from every supported stable `1.x`.
- 100 launch/stop cycles and multi-hour soaks on primary devices.
- Representative DX9/11/12 and GL/Vulkan titles on Adreno and Mali.
- Link pairing, vault restore, fabric rollback and scanout fallback fixtures pass.
- No unresolved critical security, data-loss, rendering-corruption or input regression.
- Published limitations. No unsupported performance claims.

## Explicit non-goals

- Shipping or indexing copyrighted games.
- DRM bypass or kernel anti-cheat claims.
- Downloaded third-party shader caches.
- WAN remote play, accounts as a requirement, or gameplay telemetry by default.
- Root, custom kernels, OverlayFS that needs a patched Android.
- Forced clocks, fan firmware or global scheduler changes.
- A `2.0.0` tag on a build that only finishes `1.8.0` leftovers.

Layer-by-layer emulation work that is not one of the eight majors stays in [EMULATION_ROADMAP.md](EMULATION_ROADMAP.md). Container layers stay in [CONTAINER_PLATFORM.md](CONTAINER_PLATFORM.md).
