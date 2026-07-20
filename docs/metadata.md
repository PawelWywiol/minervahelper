# Game metadata and cover art — research notes

Status: **first slice shipped 2026-07-20** — the detail pane shows canonical title,
system and cover art (R41, [D21](decisions.md#d21)) from the two keyless sources
below. Still open: game descriptions (need an API key) and the offline
hash-to-name database. The archive itself carries no game descriptions or images
(see the R5 note in [requirements.md](requirements.md)).

One correction from implementation: Hasheous's metadata *proxy* endpoints
(descriptions, IGDB covers) turned out to require an API key — only the plain
hash lookup is anonymous. And No-Intro zip hashes do match its signatures, so
coverage is better than the research below feared; TOSEC zips still miss.

## What we have to search with

For every file the catalogue knows: file name, size, md5, sha1, and a platform
derived from the archive path. Hashes are the reliable key — names are messy.
One caveat: for zipped arcade sets (MAME, FBNeo) the hash of the zip matches no
public database; there the **set name** (`progear`) is the key instead.

## Where the data could come from

| Source | Match by | Needs an account? | Gives | Fit |
|---|---|---|---|---|
| **libretro-database** (GitHub) | md5/sha1/crc, and set name for arcade | No | Proper game title, system, region, year | **Best for names** — free files, can be converted once into a small local database and queried offline |
| **libretro thumbnails** (thumbnails.libretro.com) | game name from the database above | No | Box art / title screen / snapshot as plain image URLs | **Best for covers** — one simple download per image, no keys |
| **Hasheous** (hasheous.org) | md5/sha1 directly | No | Title, description, cover (relayed from IGDB) | **Best for descriptions** — one anonymous request per game; community-run, so treat as optional extra, never a dependency |
| ScreenScraper.fr | md5 + file name | Yes — every user needs their own account | The richest data of all | Rejected: per-user credentials are exactly the hassle this app avoids |
| IGDB, MobyGames, TheGamesDB, SteamGridDB | name only | Yes (some paid) | Varies | Rejected: no hash lookup and/or gated keys |
| OpenVGDB | md5/sha1 (prebuilt database) | No | Title, description, cover links | Right shape, but abandoned since ~2022 with dead image links — a design reference only |

## The recommended shape, if ever built

1. **Title + system, offline:** convert libretro-database into a hash → name table,
   shipped and updated like the catalogue itself. Works for all 2M files with no
   network. Medium effort.
2. **Cover, on demand:** on the detail screen, build the thumbnail URL from that
   name and fetch it once, caching to disk. Small effort.
3. **Description, on demand:** one Hasheous request by hash on the detail screen,
   cached, silently skipped when offline. Small effort.
4. **Arcade:** map set name → title via libretro's MAME list (also unlocks their
   MAME cover art); optionally bundle the free `history.xml` for descriptions.

Box art is community-collected and copyrighted; every emulator frontend fetches it
the same way. Fetch-and-cache, never bundle images into the app.
