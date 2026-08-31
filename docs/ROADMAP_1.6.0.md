# OpenNative 1.6.0 Roadmap

## Goal

`1.6.0` is a runtime-quality milestone on the current package and container architecture. It must make Wine, prefixes and guest launch cheaper and more predictable without migrating application identity or introducing a downloadable modular-runtime store.

`2.0.0` still owns identity migration and independently versioned Wine/Proton packages. `1.6.0` only changes how the existing ImageFs, container, wineserver and presentation path start, idle and recover.

Work may proceed in parallel with remaining `1.5.0` measurement (Thor soak, Perfetto). A `1.6.0` APK must not claim a frame-rate gain until the [performance method](PERFORMANCE.md) is satisfied.

## Product outcomes

- First launch of a warm prefix reaches the guest executable without repeating wineboot, Mono/Gecko install or DLL overlay copies.
- The player can see container boot stages: ImageFs ready, prefix activate, wineserver up, graphics bind, first frame.
- Shared-prefix games remount `A:` and apply per-game overlays without recreating the container or killing wineserver unless the runtime actually changed.
- Wine services, debug channels and Steam helpers stay off unless the title needs them.
- Prefix size is explainable: Windows, users, temp, crash dumps, installer leftovers, shader caches.
- Optional trim and snapshot actions preview what they will delete or copy and always keep a rollback.
- Compatibility Doctor records wineserver, prefix and ImageFs timings in the existing launch timeline.
- Every title has an isolation tier, a launch recipe hash and a container state (`Warm` `Cold` `Dirty` `Broken` `Locked`).
- Last launch is explainable stage-by-stage against the previous successful launch (TTFF, wineserver, layer apply, RSS).
- Slot B holds the last-known-good mutable prefix; Flip to slot B is one action and does not delete slot A until the user confirms.
- Play, install and maintain I/O cannot run over each other. Catalog and trim wait.
- Same-container activate does not delete `home/xuser` and relink.

## Delivery sequence

### Stage 0: boot contract

- Define a local launch-stage clock: container activate, ImageFs validity, prefix marker, wineserver pid, graphics bind, first presented frame, clean wineserver stop.
- Persist a prefix generation marker: Wine/Proton id, wincomponents hash, DX/VK wrapper digest, locale, and last clean shutdown.
- Add query-count and I/O budgets for `ContainerManager` construction. Loading every `xuser-*` JSON on the UI thread is not acceptable.

Gate: fixture launches emit the same stage order; a missing marker forces a one-time repair instead of a silent full wineboot.

### Stage 1: warm prefix and overlay skip

- Skip `wineboot` when the prefix marker matches the selected Wine build and the last shutdown was clean.
- Skip DX wrapper, wincomponent and original-DLL copies when file digests already match the selected versions.
- Write `system.reg` / `user.reg` patches only for keys that changed since the last launch.
- Keep Mono and Gecko install-once per prefix; never re-run `msiexec` on a warm start.

Gate: a second launch of the same container performs no archive extract, no MSI, and no full registry rewrite. Interrupting a warm start cannot leave a half-written marker.

### Stage 2: wineserver and Wine services

- Treat wineserver as a measured service: start, ready, idle RSS, stop, and forced `wineserver -k`.
- Do not issue `wineserver -k` between sequential pre-install or interface-generation steps unless a new Wine prefix or architecture is required.
- Keep `WINEESYNC=1` as the default. Evaluate FSYNC or NTSYNC only as a per-title A/B with rollback; never enable them globally from a device name.
- Default game launches to essential Wine services. Compatibility (full services) and safe launch remain explicit presets.
- Leave `WINEDEBUG` silent on the play path. Doctor and support-bundle capture may enable bounded channels after the fact.

Gate: parent-then-child installer fixtures still wait for process-tree quiescence. A sync-primitive change that worsens p95 or increases wineserver crashes is rejected.

### Stage 3: shared prefix and drive mapping

- Remap `A:` (and other game drives) without deleting the `xuser` symlink when the same shared prefix is already active.
- Apply per-game executable, env, graphics and input overlays after drive map, not by rewriting the shared prefix defaults.
- Hold a prefix lock so two installs or two games cannot mutate `SHARED_PREFIX` at once.
- Detect a dirty shared prefix after an installer (new redistributables, DLL overrides, reboot-pending) and offer snapshot or move-to-dedicated.

Gate: switching two games on the shared prefix does not run wineboot. A cancelled drive remap leaves the previous mapping and lock released.

### Stage 4: prefix hygiene and guest I/O

- Show a prefix storage breakdown and estimated reclaim from temp, crash dumps, installer caches and stale winetricks leftovers.
- Add **Trim prefix** as a previewable, cancellable post-session action. It must never touch `drive_c/users/*/Saved Games`, Steam userdata, or game folders on `A:`.
- Persist Wine font and glyph caches across launches; rebuild them only after a Wine or locale change.
- Diagnose FUSE/SAF and case-folding failures before the guest hits `ERROR_PATH_NOT_FOUND`.
- Stream large guest copies with the same bounded buffers used by provider transfers.

Gate: trim fixtures prove saves and game files survive; a low-space trim stops without deleting the rollback set.

### Stage 5: ImageFs and container activate

- Validate ImageFs with a generation file instead of walking the whole rootfs on every launch.
- Extract graphics-driver and extras archives incrementally; skip payloads whose digest is already present.
- Make `ContainerManager.activateContainer` transactional: write a new symlink name, then replace, so a crash cannot leave `home/xuser` missing.
- Index container configs once and watch for changes; do not rescan `home/` for every settings or library recomposition.

Gate: a killed activate leaves either the previous container or a recoverable pointer, never an empty `xuser` path.

### Stage 6: guest helpers and Steam

- Start `explorer.exe` / desktop theme only for installer, file-manager or compatibility presets.
- Load `lsteamclient` and Steam pipe helpers only for Steam-owned titles, or when the user enables a Steam API overlay on a custom game.
- Keep `winhandler` process tracking; do not spawn a second handler when one is already bound to the container.
- Pause catalog, artwork and prefix-maintenance work while wineserver is in the foreground, continuing the `1.5.0` session-ownership rule.

Gate: a custom non-Steam game launch creates no Steam client DLL or pipe. Steam titles still authenticate through the existing store contract.

### Stage 7: presentation leftovers and certification

- Finish the `1.5.0` measurement work that still gates FPS claims: Perfetto around BGRA conversion, buffer retirement and SurfaceFlinger submission.
- Reuse conversion buffers by size/format and prove the steady-state path allocates nothing.
- Keep HUD, log formatting and shader-cache walks off the scanout thread.
- Run launch-cycle, shared-prefix switch and soak matrices before release.

Gate: all release criteria at the end of this document pass with sanitized evidence.

### Stage 8: isolation tiers and dirty map

- Persist `Dedicated`, `Shared compact`, `Named group` and `Lab` on the game overlay, not as a hidden boolean.
- Show cost (bytes, who shares) and risk (last dirty installer, DLL overrides) before a tier change.
- Record which title installed which redistributable or registry-mutating setup on a shared prefix.
- After a dirty installer, block the next unrelated shared-prefix launch until the player chooses Keep, Snapshot, or Evacuate.

Gate: fixtures never auto-migrate a dedicated game into shared. A dirty-map miss cannot delete another title's files.

### Stage 9: control plane, budgets and explain-last-launch

- Keep a container index (`id`, mtime, state, recipe hash, last TTFF, wineserver RSS) so library and cockpit do not parse every config.
- Record stage timings and the memory waterfall (Android available, process, wineserver, guest, wrapper, shader generation).
- Show **Explain last launch** as a delta versus the previous success. Missed warm-start budgets name the stage that ran.
- Apply soft wineserver RSS and boot-ms budgets as warnings and recommendations, never as a silent process kill.

Gate: drawing the library performs one index read, not N config parses. Doctor fixtures include a deterministic delta sentence.

### Stage 10: dual slot, repair and evacuate

- After a clean exit, rotate slot B to the current mutable prefix when the recipe is marked good.
- **Repair layer** re-applies one sealed digest (wincomponents or wrapper) without wiping users or saves.
- **Evacuate title** clones overlay + save root to a dedicated prefix, then unbinds from shared, with resume and rollback.
- **Lab launch** uses a throwaway overlay or prefix and cannot write slot A.

Gate: kill-during-evacuate restores the source binding. Repair cannot replace an unrelated layer. Lab cannot persist session overlay without a prompt.

### Stage 11: governors, watchdog and Container Studio

- Enforce play / install / maintain I/O classes. Artwork decode and prefix walk cancel when wineserver becomes foreground.
- Watchdog classifies wineserver death, lock timeout, swap cliff and hung installer into existing Doctor kinds.
- Ship Container Studio: recipe diff, dirty map, transplant title, pin container home to a granted volume, relink when it returns.
- Hot-apply env, frame cap, HUD and Safe launch without restarting wineserver. Wine build and wrapper changes still require a new launch.

Gate: play-class fixtures prove no catalog query starts after wineserver ready. Volume-missing health is not a generic Wine path error. Hot-apply cannot change a sealed layer.

The layer model, isolation tiers and control-plane fields are specified in [CONTAINER_PLATFORM.md](CONTAINER_PLATFORM.md).

## Player-facing features

| Feature | Player outcome |
| --- | --- |
| Launch profile: Fast boot / Compatibility / Safe | Fast boot skips optional Wine services; Compatibility runs a full wineboot; Safe remains the existing one-shot override |
| Boot timeline | Compatibility Doctor shows which container stage was slow or failed |
| Warm prefix badge | Library or preflight shows that the next launch can skip wineboot |
| Prefix storage | Size by Windows, users, temp, dumps, caches; trim is opt-in |
| Shared-prefix lock | A second launch or installer waits or explains the conflict |
| Drive remap status | Preflight shows `A:` target and whether the prefix will be reused |
| Sync primitive | Per-title ESYNC / FSYNC / NTSYNC only after a measured recommendation |
| Redistributable packs | Reviewed VC++, .NET, XNA, Media Foundation, PhysX installs with preview and rollback |
| Virtual desktop | Optional Wine virtual desktop for games that mis-handle Android window size |
| DPI / scaling | Per-title Wine DPI and integer-scale presentation without rewriting the prefix globally |
| Isolation tier | Dedicated, Shared, Group or Lab with bytes and risk before confirm |
| Container state badge | Warm, Cold, Dirty, Broken, Locked on the game or container card |
| Explain last launch | This TTFF and RSS versus last success, by stage |
| Dual slot | Flip to last-known-good prefix without deleting the current one first |
| Repair layer | Re-apply one sealed wrapper or wincomponent set |
| Evacuate title | Leave a poisoned shared prefix with the game and saves intact |
| Dirty map | Who installed VC++ / .NET / overrides on this shared prefix |
| Launch recipe | Shareable hash of the stack, no paths or secrets |
| Memory waterfall | Android, process, wineserver, guest, wrapper, shaders in the cockpit |
| Container Studio | Diff recipes, transplant a title, pin or relink a volume |
| I/O class | Play pauses library work; maintain never runs during a game |
| Wine service catalog | Visible list of helpers, not a hidden aggressive/essential byte |
| Missing volume | Container health names the unmounted drive instead of a Wine crash |

## Runtime optimization workstreams

### Wine

- Measure wineserver start, peak RSS and shutdown separately from the game process.
- Collapse sequential `wineserver -k` calls in `PreInstallSteps` and interface generation.
- Bound Wine debug and `+relay` so they cannot stay enabled after a Doctor session.
- Qualify essential versus aggressive `startupSelection` on the same title; promote Fast boot only when installers still work under Compatibility.
- Keep PulseAudio `PULSE_LATENCY_MSEC` as a per-title profile, not a single global of `144`.

### Container

- Replace full-directory container scans with an id/mtime index.
- Same-id `activateContainer` is a no-op; different-id activate stays transactional.
- Avoid rewriting `Container` JSON when only transient launch state changed.
- Snapshot into slot B before wincomponent, registry or redistributable mutation.
- Estimate clone cost before evacuate or "move to dedicated prefix".
- Store sealed wincomponents by digest and reference them from prefixes.
- Page-in the game executable while wineserver starts; cancel on back-out.
- Share translator code caches by Wine + Box64/FEX generation only.

### ImageFs

- Trust a signed generation marker for `opt/`, graphics extras and Proton symlinks.
- Never delete imported Wine/Proton trees during a system-file refresh (existing preserve rule stays).
- Report ImageFs extract time and bytes in the launch timeline.

### Emulation smoothness

- Do not force clocks, affinity or `TU_DEBUG` from a device model. Capability-based recommendations only.
- Distinguish CPU-translation stalls, shader compilation and SurfaceFlinger conversion in the same session summary.
- Drop obsolete guest frames before BGRA conversion when the producer outruns the display (continue `1.5.0` blit-queue work).
- Record rendered FPS and displayed FPS separately if frame generation is active.

## Acceptance criteria

`1.6.0` is release-ready only when all of the following are true:

- Modern JVM, container-activate, prefix-marker, shared-prefix lock, trim, evacuate and index suites pass in CI.
- A warm second launch of a fixture container performs no wineboot, no MSI and no wrapper extract.
- Shared-prefix game-A then game-B remaps `A:` without wineboot and without dropping the prefix lock on failure.
- Library draw reads the container index once; it does not parse every `xuser-*` config.
- Same-id activate does not unlink `home/xuser`.
- Trim cannot delete saves or `A:` game files in fixtures.
- Evacuate and slot-B flip survive kill, low space and cancel without losing the source prefix.
- Activate crash fixtures restore a valid `xuser` pointer.
- A play session starts no catalog, artwork or trim work after wineserver ready.
- Thirty launch/stop cycles complete without ANR, native crash or unbounded wineserver RSS.
- A 60-minute AYN Thor session records FPS, p95/p99, RSS, swap, temperature and wineserver RSS.
- Performance claims meet [PERFORMANCE.md](PERFORMANCE.md). Unmeasured presentation work is documented as incomplete.

## Explicit non-goals

- Application-id or storage-layout migration (`2.0.0`).
- A public downloadable Wine/Proton version store (`2.0.0` Runtime Lab).
- Automatic migration of existing dedicated prefixes into the shared prefix.
- Downloaded third-party shader caches.
- Forced device clocks, fan firmware or global Android scheduler changes.
- Persistent wineserver across OpenNative process death (Android cannot promise that).
- Reporting generated frames as native rendered FPS.
