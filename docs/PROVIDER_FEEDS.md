# Provider feed contract

OpenNative `1.2.1` reads **user-supplied** HTTPS catalogs. First launch can seed `assets/opennative-provider-tabs.json` as a default tab; the application does not embed a game catalog.

A provider tab accepts one of:

1. A versioned JSON envelope.
2. A generic RSS 2.0 or Atom feed (optional RSS field on tab creation).

## JSON envelope

```json
{
  "version": 1,
  "nextCursor": "opaque-or-null",
  "items": [
    {
      "id": "stable-provider-id",
      "title": "Example",
      "version": "1.0.0",
      "architecture": "x64",
      "size": 1048576,
      "uncompressedSizeBytes": 2097152,
      "sha256": "optional-hex",
      "artworkUrl": "https://example.com/cover.jpg",
      "description": "Optional text",
      "link": "https://example.com/file.zip",
      "profileRef": "optional-opennative-profile"
    }
  ]
}
```

Rules:

- `version` must be `1`.
- `id`, `title` and `link` are required.
- Unknown fields are stored as extra JSON and never treated as filesystem paths or commands.
- Pages are limited to 100 items. Daily refresh fetches at most three pages.

## Pagination

Feeds can be paged. OpenNative detects the style from the URL and feed kind:

| Style | Query parameters |
| --- | --- |
| WordPress REST (`/wp-json/`) | `page`, `per_page`, `orderby`, `order`, `_fields` (public title/link/excerpt/media only) |
| WordPress RSS / generic RSS | `paged`, `page`, `orderby`, `order` |
| JSON envelope | `cursor` when the feed returns `nextCursor`, otherwise `page` + `per_page` |

`orderby` is limited to `date`, `modified`, `title`, `id`, and `relevance`. `order` is `desc` or `asc`. WordPress `X-WP-TotalPages` is honored when present. The catalog can load the next page after the first three.

The catalog search field filters locally. Refresh and Load more still send paging parameters.

WordPress post HTML stores file-hoster HTTPS links and the first magnet in extra JSON. Download uploads that magnet to AllDebrid, waits until it is ready, then writes every file as siblings in public GameNative/CustomGames/<slugged-title>/ (not Android/data, which Wine cannot see through FUSE). `setup.exe` and `fg-*.bin` must share that folder.

FitGirl packs are Inno Setup 5.5 plus FreeArc `fg-*.bin` files (`ArC` magic). See [Installing games on the Thor](INSTALLING_GAMES.md). Install launches `setup.exe /DIR="D:\games\<slug>"` with Box64, writes the game to `Download/games/<slug>`, and deletes the pack after a verified game exe exists (default cleanup policy).

## Tab bundle

Settings can export and import provider tabs. The file is public metadata only:

```json
{
  "schema": "opennative.provider.tabs/v1",
  "exportedAtEpochMs": 1755705600000,
  "tabs": [
    {
      "name": "Example WordPress",
      "feedUrl": "https://blog.example/wp-json/wp/v2/posts",
      "feedKind": "JSON",
      "perPage": 100,
      "orderBy": "date",
      "order": "desc",
      "refreshPolicy": "DAILY",
      "cleanupPolicy": "DELETE_AFTER_VERIFIED_INSTALL",
      "enabled": true
    }
  ]
}
```

A WordPress REST posts URL may include `page`, `per_page`, `orderby`, `order`, or `_embed`. OpenNative stores the canonical path and then requests `page` / `per_page` / `orderby` / `order` / `_fields` only. Covers come from Jetpack media, embedded featured media, Yoast `og:image`, or the first HTTPS image in excerpt/content. Credentials and install folders are never written to the bundle.

OpenNative ships `app/src/main/assets/opennative-provider-tabs.json` as the default tab bundle and seeds it on first launch. The same file is copied at `docs/examples/opennative-provider-tabs.json` for Settings import. Use `perPage` 20 or lower when the site returns full post HTML, so a page stays under the 2 MB feed cap. OpenNative still pages with `X-WP-TotalPages`.

WordPress post HTML stores file-hoster HTTPS links and the first magnet in extra JSON. Download uploads that magnet to AllDebrid, waits until it is ready, then writes every file as siblings in public GameNative/CustomGames/<slugged-title>/ (not Android/data, which Wine cannot see through FUSE). `setup.exe` and `fg-*.bin` must share that folder.

FitGirl packs are Inno Setup 5.5 plus FreeArc `fg-*.bin` files (`ArC` magic). See [Installing games on the Thor](INSTALLING_GAMES.md). Install launches `setup.exe /DIR="D:\games\<slug>"` with Box64, writes the game to `Download/games/<slug>`, and deletes the pack after a verified game exe exists (default cleanup policy).

## RSS and Atom

The optional RSS field accepts a standard RSS 2.0 or Atom document.

Mapped fields:

- `title`
- `guid` / Atom `id`
- `link` or `enclosure@url`
- `enclosure@length` as download size
- `description` / Atom `summary` with tags stripped

WordPress HTML can include a magnet; Install sends it to AllDebrid and writes every file into the game folder.

## Transport

- HTTPS only, except loopback HTTP in tests.
- Bounded redirects, response size and artwork size.
- `ETag` / `Last-Modified` with `304` reuse of the last good snapshot.
- Credentials never appear in Room, logs, or exported settings.
