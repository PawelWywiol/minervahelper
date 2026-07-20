# Roadmap

Work is split into stages. Each stage ends with a **test on the connected device** —
a successful build does not count as done.

Last updated: 2026-07-20

## Overview

| Stage | Scope | Status |
|---|---|---|
| **M0** | Project skeleton + proving the risky path | ✅ **done** |
| **M1** | Catalogue: download, conversion, search | ✅ **done** |
| **M2** | Interface: browsing, search, details, favourites | 🔨 in progress |
| **M3** | Download queue and engine | 🔨 in progress |
| **M4** | File placement, verification, platform mapping | 🟡 |
| **M5** | Onboarding, settings, battery | 🟡 |
| **M6** | Hardening and release | 🟡 |

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

## 🔨 M2 — Interface (in progress)

- [x] **Fix gamepad navigation** — a focused button did not respond to the action
      button (found in M0). Cause: the handheld sends a gamepad button code that
      Android does not treat as "confirm". Fixed and tested on the device.
- [x] Browse by collection and platform
- [x] Search screen wired to the catalogue
- [x] ROM detail screen
- [x] Smooth scrolling over large lists (the biggest platform has 753k entries) —
      measured on the device: 1 dropped frame out of 1,028
- [x] Favourites — kept as archive paths, so they survive a catalogue rebuild
- [ ] Release memory when scrolling very far (the list currently keeps every row it
      has loaded)
- [ ] Confirm the search box can be typed into with the gamepad alone (needs the
      on-screen keyboard; only tested with injected text so far)

**Done when:** the whole app can be operated with the gamepad alone, and long
lists scroll without stutter.

## 🔨 M3 — Download queue and engine (in progress)

- [x] Selection basket for multiple ROMs — the queue *is* the basket
- [x] Group selections by torrent
- [x] Download worker (start / cancel; true pause-resume still to do)
- [x] Verify each file against the archive's checksum before accepting it
- [x] Prove the non-sharing rule: 0 bytes of file content sent, checked every run
- [x] Survive app closure — killed mid-download, resumed by itself on reopening
- [ ] Survive a device reboot (not yet tested — needs the handheld restarted)
- [ ] Find out why the large MAME torrent found no peers; two other torrents work,
      so it is that swarm or that torrent, not the engine
- [ ] Ship a tracker list (R33)
- [ ] Transfer limits
- [x] Live statistics: speed, progress, queue size, free space (R35)

**Done when:** three ROMs from one torrent plus one from another all land correctly,
the queue survives being killed and rebooted, and the user can see at a glance what
is downloading, how fast, and whether it will fit.

## 🟡 M4 — File placement and verification

- [x] Root folder picker (Storage Access Framework, survives a restart)
- [x] Editable platform → folder mapping
- [x] Flow for unmapped platforms (R18) — cleaned-up archive name, marked as a guess
- [x] Verify, then move into place
- [x] Free-space check before starting
- [ ] Confirm a placed ROM appears and launches in the emulator frontend

**Done when:** a downloaded ROM lands in the right folder and launches in an
emulator frontend; a corrupted file is rejected with a retry offered.

## 🟡 M5 — Onboarding, settings, battery

- [x] First-run flow — disclaimer, folder, catalogue, in that order
- [x] Disclaimer of responsibility (R30)
- [x] Wi-Fi enforcement (unmetered-only, done in M1)
- [ ] Data limit (R28) — confirm it is still needed; unmetered-only may cover it
- [ ] Battery audit

**Done when:** a fresh install leads to playing a downloaded game in one sitting,
entirely on the device.

## 🟡 M6 — Hardening and release

- [x] Clear error messages, with a way back (retry)
- [x] Free-space check before a torrent starts
- [ ] Edge cases: a 305k-file torrent, zero-byte files
- [x] Network loss during download — tested; it pauses and resumes by itself
- [ ] Release build with code shrinking
- [ ] Versioned catalogue re-import (R34)

---

## Open questions

Blocking nothing right now, but they need answers before the relevant stage:

| Question | Recommendation | Needed by |
|---|---|---|
| Fill in the remaining platform mappings (1,006 pairs, 10% of files)? | Add them based on actual usage, not upfront | M4 |
