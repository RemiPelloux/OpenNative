# OpenNative 1.8.0 Roadmap

## Goal

`1.8.0` is the last `1.x` product release before architecture. It productizes graphics routing, handheld extras and a single library contract on the current package id. It must not start identity migration or a public component store.

`2.0.0` takes over when this release is certified or explicitly waived for a blocking architecture need.

## Product outcomes

- A graphics preflight page shows API, wrapper, driver, required Vulkan extensions, memory budget and known incompatibilities before launch.
- Per-title primary and fallback paths for DX8/9/11/12, OpenGL/Zink and native Vulkan. Fallback is offered after a failed init, never applied silently.
- Integer scaling and aspect-correct presentation for pixel-art and fixed-resolution games.
- LSFG discovery, resize, focus-loss and fallback are hardened. Rendered FPS and displayed/generated FPS are separate numbers.
- Gyro-to-mouse and gyro-to-stick with a physical enable gesture and a visible active state.
- Steam, GOG, Epic, Amazon, Custom and provider titles share one launch / profile / Doctor contract without erasing store behavior.
- Duplicate-title grouping keeps each install and entitlement distinct.
- Cloud-save conflict UI when the backing store supports it: local, remote, newest, keep both — backup first.
- **Move game** between internal and a granted volume, resumable and verified.
- **Repair game** verifies provider or store-owned files without resetting the prefix or saves.
- Device capability report (extensions, memory, API, display) instead of a model-name allowlist. Mali is smoked, not yet a `2.0.0` certified family.
- Adapter authors can follow a documented catalog contract (`1.8` docs). Signed in-app plugins wait for `2.0.0`.

## Delivery sequence

### Stage 0: graphics contract

- Persist primary path, fallback path, wrapper pin and last successful init per title.
- Capability probe at preflight: Vulkan version, extensions, heap, driver string. Cache for the session.
- Fail closed when a required extension is missing; Doctor gets `GRAPHICS_INIT`.

Gate: missing-extension fixtures never start the guest. A successful init writes the pin.

### Stage 1: path routing and conformance

- Route DX8 (D3D8→VK or D3D8→D3D9), DX9/11 (DXVK pin), DX12 (VKD3D), GL (native or Zink), Vulkan (direct).
- After a failed init, offer the declared fallback once. Do not loop.
- Visual smoke: color, alpha, resize, fullscreen, device-loss. Failures block a “stable” label.

Gate: five-run captures per promoted path. A faster path that corrupts pixels is rejected.

### Stage 2: presentation extras

- Integer scale 1x/2x/3x and aspect-correct letterbox. No stretch-by-default for flagged titles.
- LSFG: activate, resize, focus loss, disable when base FPS is unsuitable. HUD splits rendered vs displayed.
- Per-title FIFO / mailbox / frame-cap recommendation from local evidence only.

Gate: generated frames are never reported as native FPS. Resize/focus fixtures leave a playable image.

### Stage 3: gyro, docked input extras

- Gyro profiles with a shoulder/back-button enable and an on-screen active chip.
- Docked hint: external keyboard/mouse preferred, touch overlay optional, TV-safe UI scale.

Gate: gyro cannot drift-assign after suspend. Disable gesture always works.

### Stage 4: unified library

- One `LaunchRecord` / recipe / Doctor key per installation, regardless of store.
- Group duplicate names; keep store id, install path and entitlement separate.
- Executable picker uses bounded scoring plus last success; user can override.

Gate: a GOG and Steam copy of the same title do not share a prefix unless the user chose a group.

### Stage 5: saves, move, repair

- Cloud-save conflict dialog with timestamps and a pre-resolution backup.
- Offline mode: no background catalog or cloud work during a game.
- Move game: verify, copy, swap, keep source until hash matches.
- Repair game: manifest or store verify only; prefix and saves untouched.

Gate: cancel mid-move leaves the original playable. Repair cannot delete extras the user added.

### Stage 6: capability model

- Build a device capability document at runtime. Recommendations key off that, not `Thor` / `Odin` strings.
- Smoke at least one non-Qualcomm ARM64 device. Full Mali certification is `2.0.0`.

Gate: a device with a new marketing name but the same GPU family gets the same defaults.

## Acceptance criteria

- Graphics preflight and fallback fixtures pass without live games in CI (recorded capabilities).
- Library unification does not merge entitlements or prefixes by title string.
- Move/repair/cloud-conflict kill tests preserve data.
- Integer scale and LSFG HUD honesty have screenshot or trace fixtures.
- Soak and launch-cycle bars from `1.7.0` still pass.
- No `2.0.0` migration code runs on `1.8.0` users.

## Non-goals

- New application id or public storage rename.
- OpenNative Link, Save Vault, Runtime Fabric, Adapter SDK, Console Shell (`2.0.0`).
- Downloaded shader caches, DRM bypass, forced clocks.
- Claiming Mali or a third GPU family is certified.
