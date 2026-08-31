# OpenNative 1.7.0 Roadmap

## Goal

`1.7.0` is the daily-play release. After `1.6.0` makes containers a control plane, this milestone makes launch, input, audio, display and recovery feel like a handheld console — still on the current ImageFs and application identity.

`2.0.0` still owns identity migration, the downloadable Runtime Fabric and other major architecture. `1.8.0` still owns graphics-path productization and library unification.

## Product outcomes

- Controller, touch, keyboard and mouse survive suspend, rotation, dock plug and activity recreation without reassignment.
- Audio has per-title **Stable / Balanced / Low latency** profiles with measured underrun counts, not a single `PULSE_LATENCY_MSEC`.
- Compatibility Doctor is a first-class screen: timeline, recipe, last-vs-this launch, Safe launch and repair — not a snackbar.
- Shader Health is visible per game (DXVK / Mesa / VKD3D generations, cold vs warm, size) with cleanup only after exit.
- Install queue: download now, install later while plugged in, with storage reservation shown up front.
- Storage planner estimates download, extract, prefix, rollback and shader budgets before a job starts.
- Save-aware backup of the declared save root, with preview and restore, without cloning the whole prefix.
- Secondary display and in-game drawer share one control map; losing the panel does not kill the game.
- Remaining `1.5.0` / `1.6.0` measurement (Thor soak, Perfetto) is either published or explicitly listed as incomplete. No invented FPS.

## Delivery sequence

### Stage 0: session contract

- Persist a session object: game, container, input map, audio route, display id, recipe hash, start time.
- Recover that object after Android process death (already started in `1.5.0`) including controller player index.
- Pause play-class I/O for the whole session, not only after wineserver ready.

Gate: 20 kill-and-restore fixtures keep the same player index and audio route.

### Stage 1: input product

- Qualify XInput, DirectInput, SDL, keyboard/mouse and touch overlay with hot-plug and 30-minute hold tests.
- Stable player ordering for two or more controllers; conflicts shown before launch.
- Per-game controller profile with import preview and rollback (settings-share format).
- Dead zones, analog calibration and rumble translation with visible current values.
- Touch layers: hold/toggle, radial extras, landscape/portrait layouts that do not steal the focused window.

Gate: reconnect, suspend, rotation, dialog and dual-controller fixtures pass without stuck buttons.

### Stage 2: audio product

- Instrument underruns, route changes and guest/host sample-rate conversion.
- Ship Stable / Balanced / Low latency as per-title profiles. Low latency cannot be the default if it increases underruns.
- Recover Bluetooth, USB-C, HDMI and speaker routes without restarting Wine when the device allows it.
- Keep microphone off unless the title and the user enable it.

Gate: a CPU-load soak records underrun counts; route-change fixtures do not drop the guest.

### Stage 3: Doctor and Shader Health

- Doctor home: failure kind, recipe, boot timeline, explain-last-launch, Safe, repair layer, export sanitized bundle.
- Shader Health page: per-backend generation, warm/cold, bytes, last invalidation reason.
- User-visible warning when a launch will stutter because the compatible cache is cold.
- No cache-tree walk on the frame path (existing `1.5.0` rule).

Gate: fixture failures map to one kind and one action. Shader cleanup cannot run during play.

### Stage 4: install queue and storage planner

- Queue provider and installer jobs with **Now** and **When charging**.
- Planner shows download, expanded, prefix growth, rollback and free-space remaining.
- One job mutates a prefix at a time (reuse `1.6.0` prefix lock).

Gate: low-space reservation fails closed before extract. Queue survives process death.

### Stage 5: save-aware backup

- Detect the save root from known Windows paths plus a user override.
- Backup / restore with file list preview. Prefix Windows and `A:` game files stay out of the default set.
- Retention limit and encryption-at-rest only if Android Keystore wraps the archive key.

Gate: restore cannot overwrite without a backup of the destination. Secrets stay out of the archive.

### Stage 6: display and cockpit

- One focus model for primary game surface, drawer and presentation display.
- Dock plug/unplug keeps the game; UI moves.
- Cockpit Performance tab shows memory waterfall and input/audio route, not only FPS.

Gate: 20 hot-plug cycles. Loss of the second display returns controls to the primary surface.

## Acceptance criteria

- Input and audio fixture matrices pass in CI where they can be simulated, and on Thor for hardware routes.
- Doctor and Shader Health add no network upload.
- Install-queue and save-backup kill/low-space fixtures restore source data.
- Thirty launch/stop cycles and one 60-minute soak with FPS, p95/p99, RSS, swap, temperature, underruns and wineserver RSS.
- Performance claims follow [PERFORMANCE.md](PERFORMANCE.md).

## Non-goals

- Identity or storage-layout migration (`2.0.0`).
- Downloadable Wine/Proton version store (`2.0.0` Runtime Fabric).
- Native Vulkan scanout as the default (`2.0.0` Presentation Engine).
- Gyro-to-mouse, integer scaling productization and full library unification (`1.8.0`).
- Forced clocks or downloaded shader caches.
