# Contributing to OpenNative

OpenNative accepts focused bug fixes, compatibility work, tests, documentation and evidence-backed performance changes.

## Before coding

1. Search existing OpenNative issues and relevant upstream component changes.
2. Describe the observed behavior and the smallest proposed change.
3. For performance work, capture a baseline before editing code.
4. Keep unrelated refactors out of the same pull request.

## Development rules

- Preserve GPL notices, upstream copyrights and third-party attribution.
- Never commit credentials, store tokens, game files, saves, firmware, private paths or signing keys.
- Keep generic Android ARM64 behavior working; device-specific tuning must be opt-in and reversible.
- Do not hide native assertions, clamp invalid memory ranges or weaken synchronization to make a crash disappear.
- Avoid unsafe compiler flags, clock changes and Android-global settings.
- Add tests proportional to the changed behavior.

## Test discipline

- Run the smallest relevant JVM class or package while iterating.
- Do not run concurrent Gradle builds in one worktree; Kotlin incremental caches are shared.
- Reserve the full modern JVM suite for CI or a machine with sufficient disk and memory.
- Use `./gradlew clean` to remove generated build and test outputs; never commit them.
- Do not weaken assertions, hide failures or exclude a test solely to make a build green.

## Performance evidence

Include device, Android build, SoC/GPU, driver, game/version, scene, resolution, frame cap, container profile and thermal starting point. Compare at least five warmed runs and report median FPS, p95/p99 frametime, RSS and temperatures. Raw logs must be scrubbed of private data.

## Pull requests

Explain the problem, root cause, implementation, risks, fallback and validation. UI changes should include screenshots from relevant screen sizes. Native changes should identify buffer/thread ownership and shutdown behavior.

By submitting a contribution, you confirm that you have the right to license it under the repository's GPL-3.0 license while retaining your copyright.
