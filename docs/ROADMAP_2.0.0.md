# OpenNative 2.0.0 Roadmap

## Goal

`2.0.0` is an architectural release: OpenNative-owned identity and data migration, modular runtime components, broader device validation and a stable compatibility contract. Work starts only after the `1.5.0` quality gates are met.

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

Exit criteria: runtime modules can be upgraded or rolled back independently, manifests reject tampering, and compatibility validation covers supported combinations.

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

Exit criteria: no backend relies on a hidden device-specific default, visual conformance tests pass, and target workloads meet the performance gates in `PERFORMANCE.md`.

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
