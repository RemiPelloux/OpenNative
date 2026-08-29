# Provider feed contract

OpenNative `1.3.1` reads **user-supplied** HTTPS catalogs. Launch seeds `assets/opennative-provider-tabs.json` (FitGirl JSON and Skidrow RSS) when those tabs are missing. The application does not embed a game catalog.

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

`orderby` is limited to `date`, `modified`, `title`, `id`, and `relevance`. `order` is `desc` or `asc`. WordPress `X-WP-TotalPages` is honored when present. After the first three pages, later pages load as the grid scrolls.

The catalog search field queries the whole WordPress catalog (`search=`). Results are not limited to the pages already cached. Further matches load as the grid scrolls.

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

The shipped **Skidrow** tab is stored as `https://feeds.feedburner.com/SkidrowReloadedGames`. Browse reads the live HTML archive (`https://www.skidrowreloaded.com/` and `/page/N/`). Search uses `https://www.skidrowreloaded.com/?s=<query>&feed=rss2`. The site WordPress REST API and `/feed/` permalink are blocked (403). Later archive pages load as the grid scrolls. Download keeps only 1fichier links and scrapes the post page when the listing has none. Those links are unlocked through AllDebrid (`/v4/link/unlock`), including a labeled post password when AllDebrid reports `LINK_PASS_PROTECTED`. Magnets, Mega, and other hosters on that tab are ignored. OpenNative extracts returned RAR, 7z, ZIP, or verified ISO9660 payloads into Custom, copies the catalog artwork as `cover.jpg` / `cover.png`, and deletes the source archive. Portable releases launch the discovered game exe directly; installer-only payloads run `setup.exe` through Wine and delete the installer pack only after a playable installed executable is verified.

## Transport

- HTTPS only, except loopback HTTP in tests.
- Bounded redirects, response size and artwork size.
- `ETag` / `Last-Modified` with `304` reuse of the last good snapshot.
- Credentials never appear in Room, logs, or exported settings.
