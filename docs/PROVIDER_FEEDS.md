# Provider feed contract

OpenNative `1.2.0` reads **user-supplied** HTTPS catalogs. The application does not ship a catalog, default feed, or third-party index.

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

The catalog search field filters locally immediately. After a short debounce it also sends WordPress REST `search=` or RSS `s=`.

OpenNative still does not scrape HTML bodies or extract magnets.

## RSS and Atom

The optional RSS field accepts a standard RSS 2.0 or Atom document.

Mapped fields:

- `title`
- `guid` / Atom `id`
- `link` or `enclosure@url`
- `enclosure@length` as download size
- `description` / Atom `summary` with tags stripped

OpenNative does not scrape HTML bodies, extract magnets, or special-case any host.

## Transport

- HTTPS only, except loopback HTTP in tests.
- Bounded redirects, response size and artwork size.
- `ETag` / `Last-Modified` with `304` reuse of the last good snapshot.
- Credentials never appear in Room, logs, or exported settings.
