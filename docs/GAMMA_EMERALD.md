# Gamma Emerald on OpenNative

This is the current conservative AYN Thor baseline. It targets stable play and moderate heat before visual quality.

## Container baseline

| Setting | Value |
| --- | --- |
| Resolution | 1280x720 |
| Frame cap | 30 FPS |
| Container | Bionic |
| Emulator | FEXCore 2605 |
| Wine | Proton 10 ARM64EC |
| DirectX wrapper | DXVK 2.4.1 GPL async |
| Graphics driver | Wrapper v2 / compatible Turnip |
| DXVK / wrapper memory budget | 4096 MB |
| SurfaceFlinger compatibility | Enabled |
| SDL controller API | Enabled |
| Controller slots | One unless local multiplayer is configured |
| Native CPU list | 0-7 |
| WoW64/FEX CPU list | 4-7 |
| OpenNative game pinning | Disabled |

Keep `WINEESYNC=1`, the Mesa shader cache enabled, and a 30 FPS wrapper cap. Set both `maxDeviceMemory` fields to `4096` instead of leaving guest-visible memory unlimited. The game reached more than 3 GB RSS and 1.6 GB swap with unlimited budgets on the 12 GB Thor, while Android had only about 600 MB available. Do not combine several experimental environment variables at once: duplicate frame caps or conflicting present modes make diagnosis harder.

The native and WoW64 masks above are container settings, not a request for OpenNative to force CPU affinity. OpenNative's power-control game pinning should stay disabled for this profile so FEX receives the configured WoW64 mask and Android remains free to schedule the host threads.

## Shadows

The working shadow profile uses Unreal Engine `sg.ShadowQuality=1`. This retains basic shadows while disabling costlier effects such as high post-processing, foliage and reflections. If sustained temperatures or frametime spikes become unacceptable, set shadows back to `0`; this changes visual quality, not save data.

## Controller checks

1. Enable the physical controller in the container controller tab.
2. Keep SDL controller support enabled.
3. Configure only player 1 for a single-player game.
4. Fully stop and relaunch the container after changing controller provisioning.
5. If the game menu ignores input, verify Android sees the device and that OpenNative is not showing its own quick menu over the game.

## Validation scene

Use the same save, camera position and 60-second route for every comparison. Warm the device first, record five runs, and compare p95/p99 frametime as well as average FPS. A 30 FPS average can still feel poor if shader compilation produces long spikes.

## Measured Thor baseline

The August 16 capture on the AYN Thor Max produced 23.28 average FPS, 25.01 median FPS and 68.46 ms median p95 frametime. CPU averaged 75.39% while GPU averaged only 31%; CPU and GPU peaks reached 88 C and 84 C. This points to CPU translation, thermal throttling and memory pressure before raw GPU saturation. Re-test after a cold relaunch with the 4096 MB budget; a higher-resolution or shadow-quality experiment is not meaningful until swap and p95 frametime improve.
