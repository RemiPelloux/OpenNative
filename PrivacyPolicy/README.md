# OpenNative Privacy Policy

Last updated: August 18, 2026

OpenNative is an independent open-source Android application. It does not operate an account service, advertising network or gameplay telemetry backend.

## Data stored on the device

Store credentials, session tokens, container settings, saves, logs, shader caches and performance captures are stored locally in OpenNative's application storage or in folders explicitly selected by the user. OpenNative does not upload diagnostic bundles automatically. An export leaves the device only when the user shares it.

## Online services

OpenNative contacts a game store only when the user signs in to or uses that store integration. Steam, Epic Games, GOG, Amazon and Nexus Mods process those requests under their own policies.

OpenNative does not send gameplay feedback, compatibility reports, device statistics or recommendation requests to GameNative APIs. The application currently downloads some Wine, graphics-driver and translation components from an inherited binary host. These requests reveal ordinary network metadata such as the requesting IP address to that host, but do not include store credentials, saves or game files. This dependency is tracked in [the independence document](../docs/INDEPENDENCE.md).

## Local diagnostics

Performance metrics and crash logs are generated locally. Diagnostic exports are designed to redact credentials and local paths, but users should review every file before publishing it. Never share store tokens, game files, saves, firmware or signing keys.

## Data deletion

Users can remove local data by signing out, deleting individual containers, clearing the application's Android storage or uninstalling OpenNative. OpenNative has no first-party server account to delete.

## Changes and contact

Policy changes are published in this repository. Questions and deletion concerns can be filed through [OpenNative Issues](https://github.com/RemiPelloux/OpenNative/issues) without including private data.

## Historical attribution

OpenNative derives from GameNative source code. That attribution is preserved for licensing and history; GameNative does not operate OpenNative or this policy.
