# OpenNative 2.0.0 Roadmap

## Goal

`2.0.0` is an architectural release: OpenNative-owned identity and data migration, modular runtime components, broader device validation and a stable compatibility contract. Work starts after the `1.5.0` quality gates are met. Wine warm-start, container activate and prefix hygiene that fit the current architecture belong in [`1.6.0`](ROADMAP_1.6.0.md) and may proceed in parallel.

## 1. Identity and data migration

- Define the final OpenNative application ID and public storage layout.
- Build a resumable migration that inventories, stages, hashes and atomically promotes containers, saves, profiles and cache metadata.
- Never delete the source installation automatically; provide verification and rollback reports.
- Version every persisted schema and test upgrades from each supported OpenNative release.
- Keep legal attribution, third-party notices and Git history intact.

Exit criteria: interrupted, low-space and corrupted-file migrations recover safely; old and new builds can be distinguished; no save or container is orphaned.

## 2. Modular runtime architecture

- Introduce stable interfaces for Wine/Proton, Box64/FEX, graphics drivers and DirectX wrappers.
- Resolve component compatibility through signed manifests with immutable versions and hashes.
- Isolate download, validation, extraction and activation so a failed component cannot damage a working runtime.
- Make renderer and translation-runtime selection capabilities-based instead of device-name based.
- Support rollback to the previous compatible component set.
- Model a runtime stack as an explicit lockfile: Wine/Proton, guest architecture, Box64/FEX, graphics driver, DirectX wrapper, audio layer and optional presentation features.
- Keep multiple immutable versions side by side so changing one title never mutates another title's known-good stack.
- Add compatibility constraints and preflight checks for ABI, Android API, GPU family, Vulkan extensions, wrapper/runtime pair and prefix architecture.
- Separate component download from activation; activation commits only after integrity, load and smoke checks pass.
- Provide a read-only runtime inspector showing provenance, license, digest, disk use, dependent games and rollback availability.

Exit criteria: runtime modules can be upgraded or rolled back independently, manifests reject tampering, and compatibility validation covers supported combinations.

### Compact containers and shared prefix groups

- Reuse the `1.6.0` prefix generation marker, transactional activate and shared-prefix lock so modular components do not reintroduce full wineboot on every launch.
- Measure container disk use by immutable runtime files, Wine prefix, redistributables, caches, saves and game-owned data before changing storage policy.
- Store verified immutable runtime/component payloads once and reference them from isolated per-game containers, preserving isolation while removing safe duplication.
- Introduce an opt-in **container group** that lets multiple compatible games share one mutable Wine prefix.
- Keep graphics, translator, display, input, environment and launch settings in per-game overlays rather than rewriting the shared group's base configuration on every launch.
- Serialize mutations and launches against a shared prefix so two installs or games cannot modify it concurrently.
- Track which game installed shared redistributables, registry changes and middleware; warn when removing one title could affect the group.
- Let a game leave a group through a verified clone to a dedicated prefix, and let a user restore the whole group from a pre-change snapshot.
- Never migrate existing isolated containers automatically. Show estimated space saved, compatibility risks and required free space before opt-in migration.

Exit criteria: compact isolated containers preserve byte-identical mutable prefixes, grouped games retain independent launch profiles, concurrent prefix mutation is impossible, and group-to-dedicated migration passes interrupted-copy and rollback tests.

### Runtime qualification matrix

- Boot a minimal 32-bit and 64-bit Windows probe for every promoted Wine/Proton and translator combination.
- Exercise process creation, registry, filesystem case behavior, common redistributables, audio initialization, XInput and a Vulkan/Direct3D smoke scene.
- Test fresh prefix, upgraded prefix and rollback paths; a new runtime cannot silently rewrite every existing prefix.
- Publish the qualified combinations and retain explicit **experimental** labels for combinations outside the matrix.

Exit criteria: a promoted component stack passes deterministic smoke tests on its declared device/GPU scope and rollback restores the prior launch result and prefix association.

## 3. OpenNative compatibility catalog

- Define a versioned, reviewable per-title profile format with provenance and exact runtime requirements.
- Keep local user overrides separate from catalog defaults and display every applied difference.
- Update profiles only with user approval and preserve rollback history.
- Collect no gameplay telemetry by default; diagnostic evidence remains local and explicitly exported.
- Reject universal presets that lack device/game evidence.

Exit criteria: catalog updates are signed, reversible and deterministic, and never overwrite a local profile silently.

## 4. Predictive optimization with safeguards

- Train only on local, bounded session summaries from the same title and device.
- Predict whether a next-launch resolution/runtime experiment is likely to improve p95 frametime or thermals.
- Require confidence, hysteresis, cooldown and an automatic rollback proposal after a failed experiment.
- Keep clocks, unsafe affinity, shader downloads and in-session renderer reconstruction outside the optimizer.
- Make every recommendation explainable from measured CPU, GPU, memory, thermal and pacing pressure.

Exit criteria: offline replay tests prove deterministic decisions, false-positive bounds are documented, and disabling the optimizer restores exact user settings.

## 5. Rendering and translation evolution

- Separate Android presentation, guest rendering and translation-runtime lifecycle behind tested ownership contracts.
- Reduce copies and synchronization only after traces establish buffer lifetime and thread boundaries.
- Add cold/warm shader and pipeline benchmarks shared across supported backends.
- Establish crash-safe cache journals and format-aware invalidation without scanning the frame path.
- Validate Qualcomm/Adreno, Mali and at least one additional Android ARM64 GPU family.
- Route DirectX 8/9/10/11/12, OpenGL and native Vulkan through explicit per-title capabilities and fallbacks rather than one universal renderer choice.
- Add visual-conformance scenes for color, alpha, depth, texture formats, presentation, resize, fullscreen and device-loss behavior.
- Instrument CPU translation startup, JIT/code-cache growth, invalidation, hot threads and synchronization stalls without sampling the frame path continuously.
- Evaluate Box64, FEX and ARM64EC/WoW64 paths per workload; recommendations apply on the next launch and always preserve the previous setting.
- Harden LSFG lifecycle, pacing and failure fallback before exposing broader frame-generation presets.
- Evaluate integer scaling and a spatial upscaler as optional presentation stages with sharpness, aspect and latency controls.

Exit criteria: no backend relies on a hidden device-specific default, visual conformance tests pass, and target workloads meet the performance gates in `PERFORMANCE.md`.

The complete layer-by-layer plan, including audio, input, storage and experimental work, is maintained in [EMULATION_ROADMAP.md](EMULATION_ROADMAP.md).

## 6. Product architecture

- Unify phone, tablet, handheld and secondary-display navigation around one state model.
- Preserve touch, controller, keyboard and accessibility focus through lifecycle changes.
- Provide per-game diagnostics, profile history and component health without nested or blocking overlays.
- Make destructive maintenance previewable, scoped and undoable where feasible.
- Complete localization of all user-visible OpenNative strings.

Exit criteria: automated state tests and device QA cover recreation, rotation, display loss, process restore and controller reconnect.

## Release certification

- Reproducible signed builds with provenance, checksums and SBOM.
- Migration tests from every supported stable release.
- 100 launch/stop cycles and multi-hour soak sessions on the primary certification devices.
- Representative games across DirectX 9/11/12 and OpenGL/Vulkan translation paths.
- No unresolved critical security, data-loss, rendering-corruption or input regression.
- Published limitations and measured results; no unsupported performance claims.
