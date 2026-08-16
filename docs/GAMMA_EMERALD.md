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
| SurfaceFlinger compatibility | Enabled |
| SDL controller API | Enabled |
| Controller slots | One unless local multiplayer is configured |

Keep `WINEESYNC=1`, the Mesa shader cache enabled, and a 30 FPS wrapper cap. Do not combine several experimental environment variables at once: duplicate frame caps or conflicting present modes make diagnosis harder.

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

