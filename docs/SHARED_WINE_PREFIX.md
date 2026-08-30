# Shared Wine prefix (1.4.0)

GameNative created one Wine prefix per game. That isolates registry and redistributables, but a large library wastes many gigabytes of duplicated Proton/Wine files.

OpenNative `1.4.0` adds a **shared prefix**, the same model GameHub and Winlator use.

## Default

**Settings → Emulation → Shared Wine prefix** is on for new games.

- The first game that needs a container creates `SHARED_PREFIX`.
- Later games map their folder to `A:` on that same prefix and keep their own executable overlay.
- Games that already have a dedicated `STEAM_*` / `CUSTOM_GAME_*` / store container stay isolated. Nothing is migrated automatically.

Turn the setting off to create a dedicated prefix for the next new game.

## What is shared vs private

| Shared | Per game |
| --- | --- |
| Wine/Proton prefix, redistributables, registry | Game files on disk |
| Graphics/runtime defaults on that prefix | `A:` drive mapping at launch |
| | Executable path overlay |

Only one game can run at a time on the shared prefix.

## Risks

One game’s installer, DLL override or registry change can affect other games on the same prefix. If a title breaks, turn **Shared Wine prefix** off and play it once so it gets a dedicated container.

Uninstalling a game removes its overlay only. The shared prefix stays so other titles keep working.

## Storage

One Wine prefix is typically a few hundred MB to a few GB. Sharing it is the main large-library saving. Game folders themselves are never merged.
