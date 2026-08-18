# OpenNative Adaptive Engine

Adaptive Engine is a per-game controller for diagnosis and conservative resolution selection. It
does not control Android clocks, fan firmware or unsafe CPU affinity.

## Modes

- **Fixed** keeps the configured container resolution and suppresses automatic decisions.
- **Observe** is the default. It predicts and reports but never stages a change.
- **Adaptive** may stage one discrete resolution step for the next launch. The running XServer is
  never resized because the generic runtime has no verified XRandR reconstruction path.

The mode and resolution bounds live in the game's container profile. A staged resolution is applied
before XServer construction on the next launch, so it cannot partially rebuild the active renderer.

## Decision model

The fast predictor uses bounded level/trend filters for five-second p95 frametime and temperature.
The classifier distinguishes warmup, CPU, GPU, memory, thermal and frame-pacing pressure.

The frame-cost estimator uses recursive least squares:

```text
T(s) = fixed_cost + gpu_cost * s^1.85
```

`s` is normalized pixel count. Samples are rejected during warmup, thermal or memory pressure,
frame-pacing instability and non-GPU-bound windows. Resolution is never lowered to hide a CPU or
memory bottleneck.

Automatic decisions require 70% confidence, consecutive evidence and a two-minute cooldown. A
probe that worsens p95 by more than 8% stages the previous resolution as a rollback.

## Shader Health

Mesa, DXVK and VKD3D use independent compatibility generations. Session start performs one active
generation scan and session end performs one comparison; there is no cache-tree work per frame.

“Clean after exit” queues maintenance after the guest and environment stop. It keeps every active
backend generation, stays below the managed cache root, retains up to three inactive generations
and targets a 2 GiB total cache budget. Game files, Wine prefixes and saves are outside its scope.

## Snapdragon policy

OpenNative discovers SoC identity, Adreno renderer, CPU frequency-policy topology and Android
Performance Hint availability at runtime. Detection is not permission to force a tuning action.
Clocks, affinity, fast-math, driver variables and presentation paths remain unchanged until an
alternating target-device A/B passes the performance gates in [`PERFORMANCE.md`](PERFORMANCE.md).

## Diagnostics

The share action creates a fresh report containing the current metrics, prediction, memory,
resolution, device capabilities and shader state. Credential-like values and Android/host paths are
redacted, and source log ingestion is capped at 2 MiB. Saves, game binaries and shader binaries are
never included.
