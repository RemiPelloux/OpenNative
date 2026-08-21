# Installing games on the Thor (no PC)

OpenNative can take a FitGirl pack or a Skidrow archive from a provider tab all the way to Play, on the AYN Thor. You do not need a PC. FitGirl unpack is slow. That is the tradeoff.

## Skidrow (extract, then Play)

Skidrow is extract-only. There is no Wine `setup.exe` step.

1. Open the Skidrow tab. Browse is the live site archive; search is RSS.
2. Open a game. Tap **Extract to Custom**. Stay on the device until the bar finishes.
3. OpenNative unlocks the 1fichier link through AllDebrid, downloads the rar, extracts it into `GameNative/CustomGames/<slug>/`, copies the catalog cover as `cover.jpg` or `cover.png`, and deletes the archive.
4. Open **Custom** and tap **Play**. OpenNative launches the game exe. Crash handlers (`UnityCrashHandler64.exe`), redistributables and setup tools are ignored.

If Play ever opened the Wine file manager instead of the game, that was the old unique-exe check. `1.3.0` picks the real game exe automatically. You can still open the container and tap a specific `.exe` if you want another one.

## What you do

1. Open the FitGirl (or other) provider tab.
2. Open a game. Tap **Download**. Stay on the device until the bar finishes. Every file lands in one folder.
3. The button becomes **Install**. Tap it.
4. Wine opens `setup.exe` with **Box64**. Do not change the emulator to FEXCore.
5. In the wizard:
   - Destination must stay **`D:\games\<game>`**. Leave it. OpenNative creates that folder.
   - Skip DirectX / Visual C++ redist.
   - Turn **on** FitGirl’s RAM limit if you see it.
   - Then let it unpack.
6. The bar can sit at 3% for a long time. That is normal. It is decompressing the first big `.bin`. Time-left is a lie.
7. Keep OpenNative in the foreground. Plug in. Do not lock the device if you can avoid it. Do not update the APK while it runs.
8. When Setup finishes, close it. OpenNative looks under `D:\games` for a real game exe and that becomes **Play** in Custom.

## Where files live

| Wine path | Android path | What it is |
| --- | --- | --- |
| `A:\` | `GameNative/CustomGames/<slugged-title>/` | The downloaded pack. `setup.exe` and every `fg-*.bin` must sit **in this same folder**. |
| `D:\` | `/storage/emulated/0/Download/` | Default Downloads drive. |
| `D:\games\<slug>` | `/storage/emulated/0/Download/games/<slug>/` | The installed game. This is the Play folder. |

Example: Darkest Dungeon pack downloads to `GameNative/CustomGames/darkest-dungeon-the-collector-s-edition-…/` with `setup.exe` next to `fg-01.bin`. Setup writes the game to `Download/games/darkest-dungeon-…`.

## Wine environment (automatic)

OpenNative sets this on every installer (`setup` / `install` / `.msi` / FitGirl pack). You should not have to open container settings.

- Emulator: **Box64** (FEXCore cannot run FitGirl’s 32-bit unpacker)
- WoW64: on
- Suspend: never, while this is an installer
- Arguments: `/DIR="D:\games\<slug>" /NORESTART`
- FitGirl extras: `WINEDLLOVERRIDES=isdone,unarc=n,b` and TEMP on `C:\windows\temp`
- `setup.exe` and `setup.tmp` are pinned to the performance cores

If you already started an old container on FEXCore, tap Install again or open Play on `setup.exe`. Box64 is forced on launch.

## Does it delete the installer?

**After a verified install, yes — by default.**

The default tab policy is `DELETE_AFTER_VERIFIED_INSTALL`. That means:

- Setup has exited.
- A real game exe exists under `D:\games\<slug>` (not `setup.exe`, not QuickSFV, not redist).
- Then OpenNative deletes the **pack** in `CustomGames` (`setup.exe` + `fg-*.bin`) to free space.
- The installed game in `Download/games` is kept.

It does **not** delete the pack if:

- Setup failed or you cancelled.
- No game exe was found (still only `setup.exe`).
- The tab policy is **Keep**.
- You asked at the end (**Ask**).

A FitGirl pack is several GB. Deleting it after a good install is what you want on a handheld. If you want to keep the bins, set that tab’s cleanup policy to Keep before Install.

## Why 3% looks stuck

FitGirl uses Inno + ISDone + FreeArc. The first file (often a huge `.bank` or `.bin`) is decompressed by a 32-bit Windows tool translated by Box64. CPU is pegged, the bar barely moves, then it jumps. A Collector’s Edition can take a long time. Leave it.

There is no faster on-device unpacker for these bins. A second “installer APK” would be the same Wine. Zip/7z portable packs are faster when the site gives you those instead of `setup.exe`.

## If it fails

- **File / Path not found:** the bins were not next to `setup.exe`, or the path had spaces/`[]`. Re-download; OpenNative now writes every magnet file as siblings and slugs the folder.
- **Hile not found:** broken Wine font for “File not found”. Same error.
- **Setup dies immediately:** container was on FEXCore. Install again so Box64 is applied.
- **APK update mid-install:** that kills Wine. Do not sideload while Setup is open.

## After it works

Custom shows the installed folder. Play launches the discovered game exe. Provider installs also show the catalog cover in Custom. You can delete leftover `CustomGames/<title>` yourself if FitGirl cleanup did not run (failed session or Keep policy).
