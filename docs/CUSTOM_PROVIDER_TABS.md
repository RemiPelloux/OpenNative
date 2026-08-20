# Custom Provider Tabs

## Product contract

OpenNative `1.2.0` places a compact `+` action immediately after the built-in **Custom** tab. Selecting it creates a user-owned library tab backed by a configurable metadata provider (HTTPS JSON envelope or optional RSS/Atom URL). The feature does not add values to `GameSource`, impersonate a store, ship a catalog, or change existing Steam, Epic, GOG, Amazon and Custom launch behavior.

Each provider tab can discover entries from a user-configured HTTPS feed, resolve a selected link through an optional AllDebrid account, download into staging, install into a user-granted directory and clean the installer after verified success.

## Library interaction

```text
Steam  GOG  Epic  Amazon  Custom  [+]  My library  Community configs
                                      ^ user-created tabs
```

The `+` is a square icon button with the tooltip **Add provider tab**. The creation flow has three steps:

1. **Identity**: name, optional icon/color and enabled state.
2. **Provider**: feed URL, refresh behavior and optional AllDebrid account.
3. **Install**: destination selected with Android's folder picker and cleanup policy.

The final screen tests the feed, credential and persisted folder permission before saving. A provider tab has an overflow menu for refresh, edit, reorder, disable and delete. Deleting a tab never deletes installed games; pending partial transfers require separate confirmation.

## Data model

`CustomProviderTab` is independent from `GameSource`:

```text
id: UUID
name: String
position: Int
enabled: Boolean
feedUrl: HTTPS URL
credentialRef: opaque encrypted-secret reference or null
installTreeUri: persisted SAF tree URI
cleanupPolicy: KEEP | DELETE_AFTER_VERIFIED_INSTALL | ASK
refreshPolicy: MANUAL | DAILY
lastGoodFeed: cache metadata
```

The Room entity contains no API key. Secrets are stored separately through an Android Keystore-backed store and are addressed by an opaque reference. Exported settings omit the reference and report only that provider authentication must be configured on the receiving device.

## Feed contract

Feeds use a versioned JSON envelope and page through opaque cursors. An item contains a stable provider ID, title, version, architecture, size, optional SHA-256, artwork URL, description, link and optional OpenNative profile reference. Unknown fields are retained when a feed is re-exported but never interpreted as filesystem paths or commands.

OpenNative requires HTTPS, limits response and artwork sizes, validates content type, follows a bounded redirect count and caches only successfully parsed pages. ETag and Last-Modified avoid unnecessary transfers. The last valid snapshot remains visible when refresh fails, with a clear stale indicator.

## AllDebrid adapter

The provider boundary exposes typed operations rather than AllDebrid response objects:

```text
validateCredential() -> AccountState
resolve(userSelectedLink) -> ResolvedDownload
```

Errors normalize to authentication, rate limit, unavailable link, unsupported host, network, timeout and malformed response. Retries use bounded exponential backoff only for safe idempotent reads. Authentication and rate-limit failures are never retried blindly.

The API key is accepted in provider settings, validated with a lightweight account request and encrypted before persistence. It is redacted from URLs, headers, logs, analytics, diagnostics and exception text. Removing an account cancels no completed download but blocks new resolutions using that credential.

OpenNative resolves only a link explicitly selected by the user. It does not crawl the clipboard, search external content indexes, bypass DRM or auto-submit every feed entry.

## Transfer and installation state machine

```text
IDLE -> RESOLVING -> QUEUED -> DOWNLOADING -> VERIFYING
     -> INSTALLING -> VERIFYING_INSTALL -> CLEANING -> READY

Any active state -> PAUSED | CANCELLED | FAILED
```

Downloads stream into a per-job `.partial` file using bounded buffers. Resume requires matching URL identity, validators and local length. The transfer service reserves space for both the installer and extracted staging data, publishes foreground progress and persists enough state to recover after process death.

Archives are inspected before extraction. Absolute paths, `..` traversal, links escaping staging, impossible size ratios, too many entries and configured size limits are rejected. Extraction never writes directly into the final directory. After verification, OpenNative promotes staging through a same-filesystem rename when available, otherwise through a verified copy-and-swap.

## Windows Installer Manager

Provider entries are classified before the install action:

- **Portable directory/archive**: verify, extract and let the user select the game executable.
- **Windows executable installer**: run `.exe` interactively in a controlled Wine installation session.
- **Windows Installer package**: run `.msi` through `msiexec /i` in the same controlled session.
- **Unknown payload**: keep as a download and require explicit user review; never execute it automatically.

Classification checks the file signature and archive structure as well as the extension. A file named `.exe` that is not a valid Windows PE executable is rejected. Silent arguments from a feed are displayed and disabled by default; OpenNative never trusts or executes arbitrary command lines received from provider metadata.

Before running an installer, the manager asks the user to:

1. create a new container or select an existing compatible container;
2. select the Wine/Proton runtime, architecture and translation runtime;
3. confirm the installer file, arguments and Windows working directory;
4. select a managed game destination exposed to Wine as a dedicated drive;
5. choose whether to keep the installer after verified success.

The default is a new container so registry, redistributables and uninstall data stay scoped to that game. Installing into an existing container requires an explicit warning and a backup of its configuration. The original installer remains in provider staging while Wine runs it; SAF content is never executed directly from an unstable content URI.

### Installation session lifecycle

```text
PREPARING_CONTAINER -> SNAPSHOTTING -> RUNNING_INSTALLER
                    -> WAITING_FOR_CHILDREN -> DISCOVERING_GAME
                    -> REVIEWING_RESULT -> VERIFYING_INSTALL -> READY

RUNNING_INSTALLER | WAITING_FOR_CHILDREN -> USER_CANCELLED | FAILED | REBOOT_REQUIRED
```

OpenNative observes the Wine process tree and prefix activity rather than waiting only for the initial process. Many installers spawn another setup process and exit early. Completion requires the installer process family to exit and a short filesystem/registry quiescence window; timeout leaves the session in **Needs review**, not falsely successful.

The session records a bounded before/after inventory of the chosen game destination plus relevant container metadata. It does not hash or scan the entire Wine prefix repeatedly. Existing `WineProcessSnapshotHelper`, pre-install command handling and executable filtering should be reused behind a dedicated installer-session API instead of duplicated in Compose.

### Result review

After the installer exits, OpenNative searches only the selected destination for new launch candidates. It reuses the existing executable policy to exclude uninstallers, setup programs, crash reporters and redistributables. The user sees:

- installer exit status and whether child processes finished;
- files/directories added or changed;
- detected executable candidates with relative paths;
- any requested reboot or missing runtime dependency;
- **Test launch**, **Choose another executable**, **Keep for later** and **Mark failed** actions.

Selecting an executable creates or updates the custom-game record and container only after confirmation. A test launch never deletes the installer. If no valid game executable is found, the job stays in **Needs review** and preserves all recovery material.

### Installer library

Every provider tab exposes an **Installers** view containing downloaded, queued, running, completed and failed jobs. Each row has stable progress and the relevant action: resume download, continue setup, review result, retry, open destination or clean installer. The global Downloads screen may mirror active jobs, but the provider tab remains their owner.

The manager stores small install receipts with provider item ID, installer hash, container ID, selected destination, final executable and cleanup result. Receipts contain no provider secret and never claim ownership of files that existed before the session.

## Cleanup guarantees

Cleanup is a post-install state, not part of download completion. `Delete after verified install` removes only the exact installer owned by that transfer job after:

1. extraction/copy or the Wine installer session completed without unresolved child processes;
2. required files exist inside the selected destination;
3. expected hashes, when supplied, match;
4. a valid final executable was selected or the feed's explicit non-executable install contract was verified;
5. the install receipt and container association were committed successfully.

If any condition fails, OpenNative keeps the installer and a sanitized failure report. A failed interactive setup may leave prefix changes, so OpenNative offers to discard a newly created installation container or keep it for recovery; it never rolls back an existing shared prefix automatically. Cancellation deletes only the job's `.partial` file after confirmation. Provider-tab deletion cannot recursively delete the selected installation directory.

## Performance constraints

- One in-flight metadata refresh per provider tab.
- One in-flight AllDebrid resolution per user-selected item, deduplicated by provider item and link identity.
- Paginated Room writes in transactions, never one query per item.
- Stable keyed Compose rows; progress updates affect only the matching job.
- UI progress is rate-limited independently from transfer throughput; byte callbacks never trigger whole-list recomposition.
- Artwork decoding is bounded and paused while a game session is active.
- Downloads and hashing use fixed-size buffers and never load a complete file into memory.
- Hashing reuses the download stream when possible and avoids an extra full read unless final verification requires it.
- Installer completion uses event/process snapshots and a bounded quiescence timer, not unbounded polling.
- Executable discovery is scoped to the selected destination and excludes unchanged directories using the installation snapshot.
- Only one interactive Wine installer session runs at a time; queued downloads may continue within thermal/storage policy.
- Feed refresh and cleanup never run in frame-delivery or game-session critical threads.

## Validation

- Unit tests: URL policy, schema compatibility, secret redaction, error mapping, retry policy, cleanup guard and path confinement.
- Repository tests: pagination, ETag/304, stale snapshot, bulk upsert and provider-tab ordering.
- Transfer tests: resume, low space, hash mismatch, archive traversal, cancellation and process recreation.
- Installer tests: PE/MSI classification, safe command construction, parent-exits-first process trees, quiescence timeout, reboot-required state, executable discovery and cleanup guards.
- UI tests: `+` placement after Custom, creation flow, controller focus, edit/delete confirmation and per-job progress isolation.
- Provider contract tests use a fake HTTPS server. CI never stores or requires a real AllDebrid key.
