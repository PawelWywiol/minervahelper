# Roadmap

Work is split into stages. Each stage ends with a **test on the connected device** —
a successful build does not count as done.

Last updated: 2026-07-21

Status key: ✅ done · 🔨 in progress · ⬜ planned — the same key
[requirements.md](requirements.md) uses.

## Overview

| Stage | Scope | Status |
|---|---|---|
| **M0** | Project skeleton + proving the risky path | ✅ **done** |
| **M1** | Catalogue: download, conversion, search | ✅ **done** |
| **M2** | Interface: browsing, search, details, favourites | ✅ **done** |
| **M3** | Download queue and engine | ✅ **done** |
| **M4** | File placement, verification, platform mapping | ✅ **done** |
| **M5** | Onboarding, settings, battery | ✅ **done** |
| **M6** | Hardening and release | ✅ **done** — signing key generated 2026-07-21; owner must back it up |
| **M7** | **User experience overhaul** — navigation, automatic queue, visual identity | ✅ **done** — owner's device-in-hand acceptance pending |
| **M8** | **Queue control and readability** — parallel downloads, priority, per-item pause, text size | ✅ **done** — owner's device-in-hand acceptance pending |
| **M9** | **Going public** — repository identity, README, licence, in-app help | ✅ **done** |

---

## ✅ M0 — Skeleton and risk spike

The point was to check the riskiest assumption first: *can we download a single
file out of a large torrent, on this hardware?* If not, the whole project falls
apart, so it had to be answered before anything else was built.

- [x] Gradle project, toolchain, build on device
- [x] Fetch a `.torrent` over HTTPS
- [x] Download one selected file out of 17 in a torrent
- [x] Verify the checksum against the archive
- [x] Confirm no other files are written

**Result:** works. 17 files in the torrent, exactly one downloaded, checksum
matched, 2 seconds. Details in [progress.md](progress.md).

## ✅ M1 — Data foundation

- [x] Download `hashes.db` with resume support
- [x] Convert it into our own catalogue
- [x] Full-text search
- [x] Background work with progress notification
- [x] Wi-Fi-only constraint
- [x] Derive platform from the file path
- [x] Platform → emulator folder mapping (first pass)

**Result:** 11-minute download, 80-second conversion, 521 MB catalogue,
19 ms search. Verified byte-for-byte against a desktop conversion.

## ✅ M2 — Interface

- [x] **Fix gamepad navigation** — a focused button did not respond to the action
      button (found in M0). Cause: the handheld sends a gamepad button code that
      Android does not treat as "confirm". Fixed and tested on the device.
- [x] Browse by collection and platform
- [x] Search screen wired to the catalogue
- [x] ROM detail screen
- [x] Smooth scrolling over large lists (the biggest platform has 753k entries) —
      measured on the device: 1 dropped frame out of 1,028
- [x] Favourites — kept as archive paths, so they survive a catalogue rebuild
- [x] Release memory when scrolling very far — the list now keeps at most 1,000 rows
      and reloads either direction; measured 2026-07-20 on the 753k-row platform:
      250 fling-scrolls deep, Java heap flat (16.7 MB → 16.4 MB), scrolling back up
      reloads the trimmed rows seamlessly
- [x] Typing into search with the gamepad alone — the system keyboard turned out to
      ignore the d-pad entirely, so Search got its own built-in keys
      ([D20](decisions.md#d20)); typed on the device with pad key events alone,
      system keyboard never opened

**Done when:** the whole app can be operated with the gamepad alone, and long
lists scroll without stutter.

## ✅ M3 — Download queue and engine

- [x] Selection basket for multiple ROMs — the queue *is* the basket
- [x] Group selections by torrent
- [x] Download worker with pause / resume / cancel (R12) — tested 2026-07-20 on a
      151 MB file: paused at 50%, resumed, the engine rechecked the kept data and
      continued from 50% within two seconds
- [x] Verify each file against the archive's checksum before accepting it
- [x] Prove the non-sharing rule: 0 bytes of file content sent, checked every run
- [x] Survive app closure — killed mid-download, resumed by itself on reopening
- [x] Survive a device reboot — tested 2026-07-20: a file queued with Wi-Fi off was
      downloaded and placed after a reboot without the app being opened at all
- [x] The large MAME torrent question is settled: the "Software List CHDs (merged)"
      swarm simply has no seeds (measured twice, 5 minutes each), while the even
      bigger "MAME - EXTRAs" torrent downloads fine — it is that swarm, not the engine
- [x] Tracker list (R33) — dropped: the torrents' own trackers are dead and DHT found
      every live swarm in every test ([D18](decisions.md#d18))
- [x] Transfer limits — dropped: unmetered-only plus the new Pause button covers every
      real need ([D19](decisions.md#d19))
- [x] Live statistics: speed, progress, queue size, free space (R35)

**Done when:** three ROMs from one torrent plus one from another all land correctly,
the queue survives being killed and rebooted, and the user can see at a glance what
is downloading, how fast, and whether it will fit.

## ✅ M4 — File placement and verification

- [x] Root folder picker (Storage Access Framework, survives a restart)
- [x] Editable platform → folder mapping
- [x] Flow for unmapped platforms (R18) — cleaned-up archive name, marked as a guess
- [x] Verify, then move into place
- [x] Free-space check before starting
- [x] Confirm a placed ROM appears and launches in the emulator frontend — confirmed
      2026-07-20 with real use: the device's frontend (Daijishō) syncs its Amiga
      platform from the app's `ROMs/amiga` folder, lists the games the app downloaded
      there, and its history shows one of them (Mortal Kumquat III) launched and
      played by the owner. Note: whether a given platform shows up is the frontend's
      own per-platform path setup — e.g. an Arcade platform was not visibly
      configured yet, so `ROMs/arcade` content waits for that

**Done when:** a downloaded ROM lands in the right folder and launches in an
emulator frontend; a corrupted file is rejected with a retry offered.

## ✅ M5 — Onboarding, settings, battery

- [x] First-run flow — disclaimer, folder, catalogue, in that order
- [x] Disclaimer of responsibility (R30)
- [x] Wi-Fi enforcement (unmetered-only, done in M1)
- [x] Data limit (R28) — dropped 2026-07-20: unmetered-only covers it
      ([D19](decisions.md#d19))
- [x] Battery audit (R29) — measured 2026-07-20: idle app holds zero wakelocks and
      0% CPU; during a real download only the necessary foreground-service locks are
      held at ~15% CPU, and every lock is released the moment the download finishes.
      Milliamp-hour totals could not be read because the device charges over the USB
      debugging cable — worth a glance at the battery screen after a day of normal use

**Done when:** a fresh install leads to playing a downloaded game in one sitting,
entirely on the device.

## ✅ M6 — Hardening and release

- [x] Clear error messages, with a way back (retry)
- [x] Free-space check before a torrent starts
- [x] Edge cases: a 305k-file torrent, zero-byte files — both download correctly;
      the torrent library's metadata parser needed its safety caps raised for the
      27 MB torrent file
- [x] Network loss during download — tested; it pauses and resumes by itself
- [x] Release build with code shrinking — tested on device, torrent path intact
- [x] Real signing key — generated 2026-07-21: RSA 4096, `CN=Minerva Helper`, alias
      `minerva`, valid to 2056, random 32-character password. A release APK was built
      and its signature checked: certificate fingerprint `FF:CF:94:2B:…:18:76`, the
      same key. The 2026-07-20 test key is archived beside it as
      `minerva-release.jks.bak`.
      **What the owner still must do:** copy `keystore/minerva-release.jks` and
      `keystore.properties` somewhere safe **outside this folder** — a password
      manager or an encrypted backup. Both are git-ignored on purpose. Lose them and
      no future build can ever update an installed app; leak them and anyone can
      publish updates that look like ours.
      **Also:** a release signed with the old test key cannot be updated by one signed
      with this key. Any such build on the device must be uninstalled first
- [x] Download progress has its own notification channel — until 2026-07-21 it
      borrowed the catalogue-sync channel, so downloads announced themselves as
      "Minerva catalog"; muting sync notifications also muted downloads
- [x] Versioned catalogue re-import (R34) — fully tested on the device 2026-07-20:
      the update banner appeared, the button was pressed, the app downloaded the
      1.76 GB index in ~9 minutes, rebuilt the 521 MB catalogue (2,428,272 files),
      recorded the archive's version, and the banner went away; favourites survived

## ✅ M7 — User experience overhaul

Added 2026-07-20 after the owner reviewed the working app: it works, but it looks and
behaves like a prototype. Full audit and rationale in [design.md](design.md).
Four packages, in order — structure before paint:

- [x] **Structure:** top bar with a back button on every screen; persistent bottom
      menu (Browse · Search · Favourites · Queue · Settings); Dev screen hidden in
      release; use the landscape screen instead of one left-pinned column —
      done and tested on the device 2026-07-20; game lists now show a list +
      details side by side
- [x] **Behaviour:** downloads start automatically on queueing (R36); finished items
      leave the queue and browse lists mark what is downloaded (R37); files already
      in the destination count as downloaded (R38); folder change offers to move
      existing files (R39) — all four done and tested on the device 2026-07-20
- [x] **Identity:** dark-first colour scheme for a gaming handheld ([D17](decisions.md#d17)),
      list rows with separation, downloaded/error badges, a real progress bar on the
      active download — done and tested on the device 2026-07-20
- [x] **Guidance:** folder rows carry an edit icon; removing from the queue asks for
      confirmation instead of firing on a bare tap; every main screen has one line of
      help; friendly empty states — done and tested on the device 2026-07-20

**Done when:** a person who has never seen the app finds a game, downloads it and
knows it succeeded — without instructions, by touch alone; and the same flow works
by D-pad alone. Tested with the device in hand, screen by screen.

## ✅ M8 — Queue control and readability

Added 2026-07-21 from the owner's use of the app on the device. Everything here was
tested on the RG477V; measurements are in [progress.md](progress.md).

- [x] **Several downloads at once** (R42), how many chosen in Settings, default 2 of
      1–4. Required one shared torrent session instead of one per download, and a
      staging folder per torrent instead of one shared folder ([D23](decisions.md#d23)).
      Non-sharing re-verified per torrent: 0 bytes of file content, every run
- [x] **The queue's order is the download order** (R43) — drag a torrent by its
      handle or use its arrows. The unit is the torrent, not the game, because a
      torrent's files are fetched together. Order survives a restart
- [x] **Pause and resume one game** (R44) without stopping the rest — takes effect
      inside a second on a running torrent
- [x] A game queued while a run is going joins that run instead of waiting for a
      separate one — found in testing
- [x] **Browse steps up one level** (R45) instead of jumping to the top
- [x] **System name and an icon on every row** (R46) — twelve drawn machine
      silhouettes in eleven vendor colours, no manufacturer logos
      ([D22](decisions.md#d22)). Non-game content is identified by file type instead.
      Redrawn 2026-07-21 after the owner found the first, medium-based set
      unintuitive: it said what the software came on, never which system it was for
- [x] **Adjustable text size** (R47), −20% to +100%, one setting for the whole app

**Done when:** the owner can say which game downloads first, stop one without
stopping the others, and read the screen at arm's length. Tested with the device in
hand.

## ✅ M9 — Going public

Added 2026-07-21, preparing the project to live at
<https://github.com/PawelWywiol/minervahelper>.

- [x] **The app's identity changed** from `com.pawelwywiol.minervahelper` to
      `io.github.pawelwywiol.minervahelper`. The old name borrowed a domain nobody owns;
      the new one is anchored to a GitHub account that is genuinely ours
      ([D24](decisions.md#d24)). Done before publication on purpose — after it, the
      identity is permanent
- [x] **A README for people who have never seen the project**, with six screenshots
      taken on the RG477V, what the app is for, how to build it and how to use it
- [x] **MIT licence** — permissive, and it disclaims warranty and liability
- [x] **A Help tab inside the app** (R48), so the guide is where the user is rather
      than in a repository they will never open
- [x] Fixed a raw NUL byte embedded in `DownloadQueue.kt`, which made the file read as
      binary to `grep`, `sed` and `diff` — it is now the escape `\u0000`, which the
      compiler treats identically

---

## Still open

| Question | Where it stands |
|---|---|
| Fill in the remaining platform mappings (1,006 pairs, 10% of files)? | Open. Adding them based on actual usage, not upfront — the unmapped-platform fallback ([D16](decisions.md#d16)) makes this non-blocking |
| Enrich ROMs with game info and cover art from external databases? | Partly done. Title, system and cover ship on the detail pane ([D21](decisions.md#d21)); descriptions need an API key and the offline name database is unbuilt — both in [metadata.md](metadata.md) |
| Filter search results by type and size (R4)? | Open, never started. Decide whether it earns its place or joins the dropped requirements |
| Light theme alongside dark? | Deferred. Dark-first shipped ([D17](decisions.md#d17)); light comes only if someone asks |

## Questions that have been answered

Kept for the record, because the reasoning still explains why the app behaves as it does:

| Question | Answer |
|---|---|
| Where do finished downloads go — vanish, or a "Downloaded" history? | History. It keeps the record R37/R38 need anyway, and users can check what they have (M7) |
| "Already downloaded" check: name+size, or full checksum? | Name+size. Checksumming a whole library would take minutes (M7) |
