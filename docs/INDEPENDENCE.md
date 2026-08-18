# OpenNative Independence

OpenNative is developed, released and supported independently. Product UI, documentation, feedback, compatibility statistics, recommendations and update flows do not use GameNative community or API services.

## Preserved compatibility identifiers

The Android application ID, internal `app.gamenative` namespace and historical storage path remain unchanged so existing installations can update without losing private containers or saves. They are implementation identifiers and are not displayed as the product name. Renaming them requires a tested migration and is not part of a cosmetic rebrand.

## Legal attribution

GPL notices, copyrights, Git history and truthful attribution to GameNative and other upstream projects remain intact. Independence does not permit removing third-party rights or presenting inherited work as original OpenNative code.

## Remaining binary-host dependency

Some component manifests still download Wine, Proton, DXVK, VKD3D, Mesa/Turnip and helper archives from `downloads.gamenative.app`. Removing those URLs before operating a verified mirror would break fresh component installs. The migration gate is:

1. inventory every archive, license, version, size and digest;
2. obtain confirmed redistribution rights;
3. publish an OpenNative-controlled immutable mirror;
4. pin SHA-256 digests and test clean/offline/repair installs;
5. switch manifests only after parity is verified.

## GitHub fork status

GitHub records repository ancestry separately from Git remotes. Removing the `upstream` remote does not detach the repository. The safe path is to ask GitHub Support to detach `RemiPelloux/OpenNative` from its fork network while preserving issues, releases, stars and URLs. Deleting and recreating the repository is intentionally not automated because it is destructive.
