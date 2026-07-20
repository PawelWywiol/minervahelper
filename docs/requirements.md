# Requirements

The current state of the app's requirements. **This is the source of truth.**

Status key:
**✅ done** (works and tested on the device) ·
**🔨 in progress** · **⬜ planned**

Last updated: 2026-07-20

---

## Search and browsing

| ID | Requirement | Status | Stage |
|---|---|---|---|
| R1 | Full archive catalogue available on the device | ✅ | M1 |
| R2 | Search by name, responding as the user types | ✅ | M1 |
| R3 | Browse the catalogue by collection and platform | ✅ | M2 |
| R4 | Filter results (type, size) | ⬜ | M2 |
| R5 | ROM detail screen (size, platform, checksums) | ✅ | M2 |
| R6 | Favourites list | ✅ | M2 |

**R5 — scope:** the detail screen shows only what the archive actually holds — name,
size, platform, checksums, source torrent.

Descriptions, release years and publishers **are deliberately not shown**. The archive
contains none, and displaying guesses parsed out of filenames would be worse than
showing nothing. Enriching the catalogue from an external metadata source is a
possible future feature, out of scope for now.

## Downloading

| ID | Requirement | Status | Stage |
|---|---|---|---|
| R7 | Select multiple ROMs at once | ✅ | M3 |
| R8 | Fetch the `.torrent` file assigned to a ROM | ✅ | M0 |
| R9 | **Download only the selected files from a torrent** (the core of the project) | ✅ | M0 |
| R10 | Group selected ROMs by torrent (one torrent = one job) | ✅ | M3 |
| R11 | Background download queue (automatic start is R36) | ✅ | M3 |
| R12 | Pause / resume / cancel at any time | 🔨 | M3 |
| R13 | Resume after the app is closed and after a device reboot | 🔨 | M3 |

## Storing files

| ID | Requirement | Status | Stage |
|---|---|---|---|
| R14 | Pick a root folder on the device | ✅ | M4 |
| R15 | Per-platform subfolders with sensible default names | ✅ | M4 |
| R16 | User can change the destination folder for any platform | ✅ | M4 |
| R17 | Automatic detection of a ROM's platform | ✅ | M1 |
| R18 | Handle ROMs that cannot be assigned to a platform | ✅ | M4 |

**R17 — how it works:** the archive has no "platform" column, so it is derived from
the file path. **R18 follows from that and is mandatory:** 21% of the archive is not
games at all (documentation, scans), and some real consoles have no equivalent in
the emulator folder standard. Without a fallback we would lock users out of 31% of
the archive. See [decisions.md](decisions.md#d5).

## Security

| ID | Requirement | Status | Stage |
|---|---|---|---|
| R19 | Verify the integrity of every file before saving it | ✅ | M0 |
| R20 | HTTPS only; cleartext traffic blocked | ✅ | M0 |
| R21 | Files reach the user's folder only after verification | ✅ | M4 |

**R19 — scope:** we check that a file **is what it claims to be** (MD5/SHA-1
checksum from the archive). This is not virus scanning — Android will not execute
a downloaded ROM as a program anyway. The limitation is spelled out in
[decisions.md](decisions.md#d6).

## User interface

| ID | Requirement | Status | Stage |
|---|---|---|---|
| R22 | Intuitive interface, legible on a small screen | 🔨 | M7 |
| R23 | **Full D-pad / gamepad operation, no touch required** | 🔨 | M2 |
| R24 | Clear download progress and notifications | 🔨 | M3 |
| R25 | First-run onboarding | ✅ | M5 |
| R35 | **Live statistics while queueing and downloading** | ✅ | M3 |
| R36 | Downloads start automatically when something is queued | ⬜ | M7 |
| R37 | Finished downloads leave the queue; browse lists mark what is downloaded | ⬜ | M7 |
| R38 | A file already in the destination (same name and size) counts as downloaded | ⬜ | M7 |
| R39 | Changing a folder asks whether to move already-downloaded files | ⬜ | M7 |
| R40 | **Finished-product look: navigation, visual identity, affordances, guidance** | 🔨 | M7 |

**R36–R40 — added 2026-07-20** after the owner reviewed the working app. The full
audit and the plan behind them: [design.md](design.md). R40 in one sentence: the user
always knows where they are, where they can go, what the app is doing, and what will
happen if they tap — with a visual identity that suits a gaming handheld, not a
template demo. Touch and gamepad are both first-class (the device has a touchscreen).

**R23 — warning:** stage M0 revealed that a focused button **does not respond** to
the gamepad action button. This must be solved in M2, otherwise the app is unusable
on the target hardware.

**R35 — what must be visible:** the user should never have to guess what the app is
doing, or find out too late that it will not fit. At minimum:

- per download: progress, speed, downloaded / total size, peer count
- for the queue: how many items are waiting, total size still to fetch
- storage: free space on the target drive, and a warning **before** starting a
  download that would not fit

## Performance and resources

| ID | Requirement | Status | Stage |
|---|---|---|---|
| R26 | Runs smoothly on modest hardware | ✅ | M1 |
| R27 | Large downloads over Wi-Fi only | ✅ | M1 |
| R28 | Data usage limit | ⬜ | M5 |
| R29 | Economical battery use | ⬜ | M5 |

## Legal position

| ID | Requirement | Status | Stage |
|---|---|---|---|
| R32 | **Never share downloaded files, directly or indirectly** | ✅ | M0 |
| R30 | Disclaimer: the author is not responsible for what the user downloads | ✅ | M5 |

**R32 is the hardest rule in the project.** The app is a download helper, never
a distribution tool. Sharing must be structurally impossible — not merely disabled
by a default setting a user could flip. Every change to the torrent engine must be
checked against this.

**Legal status is not assessed per ROM.** The app does not judge or label the legal
standing of individual files. The archive carries no rights information, so any such
label would be guesswork. What the user chooses to download is the user's
responsibility; this is stated plainly once, in the disclaimer.

*Previously listed as R31 — "a way to report copyright infringement" — dropped
2026-07-20: the app hosts nothing and distributes nothing, so there is no content
for it to receive reports about.*

---

## Requirements added during the work

Not in the original brief; they emerged from reality or from later clarification:

| ID | Requirement | Reason |
|---|---|---|
| R33 | The app must ship its own torrent tracker list | Magnet links in the archive contain none — downloads would never start |
| R34 | Re-import the catalogue when the archive is updated | The archive changes; new versions must be detected — implemented 2026-07-20 |
| R18 | Fallback folder for unmapped platforms | See above |
| R35 | Live download / queue / storage statistics | Added 2026-07-20 |
| R36–R40 | Automatic queue, downloaded markers, folder-move prompt, finished-product look | Owner's review of the working app, 2026-07-20 — see [design.md](design.md) |

## Dropped requirements

| ID | Requirement | Why dropped |
|---|---|---|
| R31 | Copyright infringement reporting | The app hosts and distributes nothing (2026-07-20) |
| — | Descriptions, release year, publisher | Absent from the archive; possible future feature (2026-07-20) |
