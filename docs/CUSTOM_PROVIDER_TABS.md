# Custom Provider Tabs

## Product contract

OpenNative `1.5.0` will place a compact `+` action immediately after the built-in **Custom** tab. Selecting it creates a user-owned library tab backed by a configurable metadata provider. The feature does not add values to `GameSource`, impersonate a store, or change existing Steam, Epic, GOG, Amazon and Custom launch behavior.

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

## Cleanup guarantees

Cleanup is a post-install state, not part of download completion. `Delete after verified install` removes only the exact installer owned by that transfer job after:

1. extraction/copy completed without error;
2. required files exist inside the selected destination;
3. expected hashes, when supplied, match;
4. the install record was committed successfully.

If any condition fails, OpenNative keeps the installer and a sanitized failure report. Cancellation deletes only the job's `.partial` file after confirmation. Provider-tab deletion cannot recursively delete the selected installation directory.

## Performance constraints

- One in-flight metadata refresh per provider tab.
- Paginated Room writes in transactions, never one query per item.
- Stable keyed Compose rows; progress updates affect only the matching job.
- Artwork decoding is bounded and paused while a game session is active.
- Downloads and hashing use fixed-size buffers and never load a complete file into memory.
- Feed refresh and cleanup never run in frame-delivery or game-session critical threads.

## Validation

- Unit tests: URL policy, schema compatibility, secret redaction, error mapping, retry policy, cleanup guard and path confinement.
- Repository tests: pagination, ETag/304, stale snapshot, bulk upsert and provider-tab ordering.
- Transfer tests: resume, low space, hash mismatch, archive traversal, cancellation and process recreation.
- UI tests: `+` placement after Custom, creation flow, controller focus, edit/delete confirmation and per-job progress isolation.
- Provider contract tests use a fake HTTPS server. CI never stores or requires a real AllDebrid key.
