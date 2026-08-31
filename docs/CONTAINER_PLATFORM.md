# OpenNative Container Platform

Winlator-style frontends treat a container as a folder plus a JSON file. OpenNative should treat it as a **control plane**: layered storage, isolation policy, launch budgets and repair that a player can understand.

This is the product difference. Games stay isolated or shared by policy, not by accident. Optimization is skip-work-that-did-not-change, not clock forcing.

`1.6.0` lands the contracts and player surfaces on the current ImageFs. `2.0.0` makes sealed layers content-addressed and independently versioned. See [ROADMAP_1.6.0.md](ROADMAP_1.6.0.md) and [EMULATION_ROADMAP.md](EMULATION_ROADMAP.md).

## Why this is different

| Typical Android Wine frontend | OpenNative target |
| --- | --- |
| One mutable prefix per game, or one opaque shared prefix | Explicit isolation tier with cost and risk shown before use |
| Copy wrappers and wincomponents on every launch | Sealed layers applied only when the digest changed |
| Symlink `xuser` and hope | Transactional activate, prefix lock, crash-safe pointer |
| Settings dump as the only “profile” | Deterministic launch recipe: layers, hashes, overlays |
| Delete prefix when something breaks | Repair the failing layer; dual-slot rollback |
| FPS overlay only | Time-to-first-frame, wineserver RSS, layer apply, last-vs-this launch |
| Shared prefix dirties silently | Dirty map: which title installed what, who is at risk |

OpenNative will not claim a game runs faster because the UI looks like a console. A faster path must prove TTFF, p95, memory or thermal improvement under [PERFORMANCE.md](PERFORMANCE.md).

## Layer model

Every running title is a stack. Upper layers cannot mutate lower sealed layers in place.

```text
ImageFs          immutable host rootfs and generation marker
Wine / Proton    sealed runtime tree (digest + provenance)
Wincomponents    sealed DLL / redistributable set
Graphics wrap    sealed DXVK / VKD3D / WineD3D / driver extras
Prefix           mutable registry, users, fonts, Wine services state
Game overlay     A: mapping, exe, env, input, display, translator
Session overlay  Safe launch, Doctor probes, one-shot overrides
```

Rules:

- Sealed layers are read-only at play time. A write materializes a private copy first.
- The game overlay never writes shared prefix defaults. It binds on activate.
- Saves live in a declared save root (`users/*/Saved Games`, Steam userdata, or an explicit reloc). Trim and clone must treat that root as sacred.
- Session overlay dies with the process. It cannot persist unless the user accepts a prompt.

## Isolation tiers

The player picks a tier. OpenNative never silently moves an existing game.

| Tier | Storage | Risk | Default use |
| --- | --- | --- | --- |
| **Dedicated** | Private mutable prefix | Lowest cross-game risk | Unknown installers, dirty titles, anything that writes registry or system32 |
| **Shared compact** | One prefix, many `A:` maps | One installer can affect others | Large libraries of already-working 64-bit titles |
| **Named group** | Shared prefix inside a labelled set | Same as shared, scoped | “Visual novels”, “Legacy 32-bit” |
| **Lab** | Throwaway prefix or overlay | Discarded after the session | Runtime experiments, Doctor clean-room |

Preflight shows estimated bytes, who shares the prefix, and the last dirty event before the player confirms a tier change.

## Container control plane

Each container exposes a local, redacted record:

| Field | Meaning |
| --- | --- |
| State | `Sealed` `Warm` `Cold` `Dirty` `Broken` `Locked` |
| Recipe | Wine, translator, wrappers, wincomponents, locale, startup profile |
| Budgets | Guest RAM hint, wrapper VRAM, wineserver RSS cap, boot-ms target |
| Last launch | TTFF, wineserver start, layer-apply ms, peak RSS, exit class |
| Delta | This launch vs last successful launch, stage by stage |
| Owners | Games bound to this prefix; redistributables each installed |
| Slots | Slot A current, slot B last-known-good snapshot |

Library and cockpit read this record. They do not open every `xuser-*/` JSON on the UI thread.

### Player actions

- **Inspect stack** — layers, digests, disk, dependents.
- **Explain last launch** — which stage grew vs last time.
- **Repair layer** — re-apply one sealed layer from the content store.
- **Promote / demote isolation** — dedicated ↔ shared ↔ group, with copy estimate.
- **Evacuate title** — verified clone to dedicated, then unbind from the shared prefix.
- **Flip to slot B** — one-action rollback of the mutable prefix.
- **Trim** — preview reclaim; never saves or `A:`.
- **Export recipe** — settings-share format; no paths, secrets, saves or binaries.

## Launch budgets

Optimization is a budget, not a slogan.

| Budget | 1.6 intent | Fail behavior |
| --- | --- | --- |
| Time to wineserver ready (warm) | Skip wineboot and matching copies | Miss → show the stage that ran; do not hide a required wineboot |
| Time to first presented frame | Record only; no clock chase | Compare to last launch; offer Safe or Compatibility |
| Layer apply | 0 bytes copied when digests match | Digest mismatch copies that layer only |
| Wineserver RSS | Soft cap from device class | Warn and offer trim / dedicated; never kill the game silently |
| Guest + wrapper RSS | Keep the existing 9–14 GB → 4096 MB default when unlimited | Never override an explicit user limit |
| Background I/O | Catalog, artwork, hash, trim paused while wineserver is foreground | Queue with cancellation |
| Activate | Same-container activate is a no-op (no symlink delete) | Crash leaves previous pointer |

Parallelize only independent work: ImageFs marker check, sealed-layer digest verify, and game-exe existence can run together. Registry patch, drive map and wineserver start stay ordered.

## I/O and memory governors

Three sessions, three priorities:

1. **Play** — wineserver and presentation win. Library refresh, icon extract, feed paging and prefix walk stop.
2. **Install** — process-tree and staging win. Play-path caches are not rebuilt mid-installer.
3. **Maintain** — trim, snapshot, layer verify. Only after a clean guest exit, with an explicit I/O byte budget.

Memory waterfall (observe, then recommend, never force clocks):

1. Android available / swap.
2. OpenNative process RSS.
3. Wineserver RSS.
4. Guest exe RSS.
5. DXVK / VKD3D / wrapper budget.
6. Shader-cache generation size.

Cockpit Performance tab shows this waterfall. Adaptive Engine may stage a next-launch resolution step when GPU-bound; it must not drop resolution to hide a Wine or translator leak.

## Self-heal and dual slot

Delete-the-prefix is the last action, not the first.

1. Classify: missing sealed file, dirty registry, broken symlink, incomplete marker, wineserver crash, Android process death.
2. Repair the smallest layer that restores a known-good recipe.
3. If the mutable prefix is the fault, offer **Flip to slot B** (last clean snapshot).
4. If the title poisoned a shared prefix, offer **Evacuate** that title only.
5. Lab launch tests a recipe without writing slot A.

Interrupted snapshot, clone or evacuate resume or roll back. Low space stops before deleting the source.

## Watchdog

A local watchdog observes the container, not the player:

- Wineserver alive after ready, or Doctor gets `WINE_FAILURE` with pid and last stage.
- Prefix lock held past a timeout → explain the owner (game vs installer), do not steal the lock.
- OOM / swap cliff → session summary records the waterfall; next launch may propose Dedicated or a lower wrapper budget.
- GPU reset / device-loss → existing visual-conformance path; do not recreate ImageFs.
- Hung installer child → existing needs-review state, not success.

No continuous gameplay upload. Watchdog rings stay on device.

## Container Studio (player lab)

A single screen for people who want control:

- Diff recipe A vs B (runtime, wrappers, env, wincomponents, drives).
- See the dirty map for a shared prefix.
- Transplant a title: copy overlay + save root, leave Windows behind or take it.
- Pin a container home to internal or a user-granted volume. Relink when the volume returns; do not recreate.
- Run a Lab session against a snapshot.

Studio cannot become a hidden remote shell. It operates on declared layers and granted trees.

## Optimization backlog that is unique to this model

These are committed for design in `1.6.0` and implementation when fixtures exist:

- **Same-id activate is a no-op.** Today `activateContainer` deletes `home/xuser` and relinks even when the container is already active.
- **Sealed wincomponents store.** One digest of `vcrun2010` / `direct3d` / … referenced by many prefixes.
- **Hot overlay** for env, frame cap, HUD and Safe launch without wineserver restart. Wrappers and Wine build still require a new launch.
- **Translator cache keyed by Wine + Box64/FEX generation**, shared across titles that use that pair, invalidated only on ABI change.
- **Page-in the game executable while wineserver starts**, cancelled if the user backs out.
- **Container index** (`id`, mtime, state, last TTFF) instead of parsing every config to draw the library.
- **Launch recipe hash** in Doctor and settings share, so two devices can compare stacks without paths.
- **Per-title Pulse and Wine-service catalog** with a visible enable list, not a single aggressive/essential byte.
- **External-volume container home** with health `Missing volume` instead of a generic Wine path error.
- **Startup graph** in Doctor: which work was serial, which was parallel, which was skipped.

## Non-goals

- OverlayFS or kernel modules that require root or a custom Android build.
- Persistent wineserver after OpenNative is killed.
- Automatic move of existing dedicated prefixes into Shared.
- Downloaded shader caches or unreviewed winetricks scripts.
- cgroup / nice / affinity changes that affect other Android apps without an opt-in per-title mask.
- Claiming TTFF or FPS wins without A/B captures.
