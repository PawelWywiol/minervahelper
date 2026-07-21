# Work log

What was done, when, and what it measured. Newest first.

Each entry records what actually happened — including what failed and what is still
unresolved. Claims without measurements do not belong here.

---

## 2026-07-21 — M8: a queue the owner steers, and text you can read

Five things the owner asked for after using the app on the device. All done and
tested on the RG477V.

**Several downloads at once, and you choose how many.** The engine used to work
through one torrent at a time. It now runs up to four side by side, set in
Settings, default two. Measured on the device: two torrents reported progress in
the same second, and **each one uploaded 0 bytes of file content** — the
non-sharing rule was re-checked per torrent, because the change was structural.

Two faults had to be fixed to make it safe. Every download used to open its own
torrent session, which would have multiplied peer discovery — the expensive part
on this hardware — so they now share one. And every download used to stage files
in the same folder, which one finishing wiped clean; with downloads running side
by side that would have destroyed a neighbour's half-finished data. Each torrent
now stages in its own folder.

**A game added mid-run no longer waits for a whole separate run.** Found while
testing: with the limit at two, a third game queued during a download sat idle
until everything before it finished — "downloads at once: 2" quietly doing one.
The worker now re-reads the queue between batches.

**Order the queue yourself.** Drag a torrent by its handle, or use its arrows.
Both were tested; both persist. The unit is the torrent, not the single game,
because a torrent's files are fetched together — per-game order would have been
a fiction. Order survives a restart (the queue database moved to version 4 on the
owner's real data, with the download history intact).

**Pause one game, not everything.** A paused game drops out of a running torrent
within a second — measured — while the rest keep going; the engine simply stops
wanting that file. Whole torrents can be paused too, and "Pause all" is still
there.

**Browse steps up one level.** It used to jump straight to the top and throw away
the levels in between. From a game list it now lands on that collection's
platforms, and only the next press reaches the collections.

**Rows say what system a game is for, with an icon.** Browse rows showed only a
name and a size. They now read "Sega Mega Drive · 270 kB" with a small icon of
the medium — cartridge, disc, floppy, tape, arcade cabinet, handheld, computer,
document. A trap found on the device: whole collections map to one folder, so
FinalBurn Neo's Mega Drive games were labelled "Arcade" on a screen titled
"megadrive". The platform's own name now wins for the label, while the file still
lands in the `arcade` folder as before.

**Text size is adjustable**, −20% to +100%, in Settings. Because nothing in the
app hardcodes a text size, one setting reaches every screen. Checked at +100% on
the device: the two-pane game screen still works and the bottom menu is not
clipped, so no separate small-screen layout was needed.

**One rendering bug found and fixed while testing.** Moving a torrent to the top
with the arrows made it appear to vanish — the list keeps its scroll anchored to
whatever was on top, so the promoted group ended up above the visible area. The
database was correct throughout; only the view had slid. It now stays put.

Not fixed: the Lost Level torrent used in testing has no sharers at all (0 peers
over several minutes), so it never progressed. That is the archive's swarm, not
the app — the same run downloaded three other torrents without trouble.

---

## 2026-07-20 — Owner's M7 review fixes, game covers, and release signing

The owner accepted M7 with three fixes, all done and tested on the device:

**Browse is a home button now.** Tapping Browse from deep inside a platform list
returns to the top-level collections screen instead of restoring the sub-screen.

**Downloaded history can be cleaned.** Tapping an entry in the Queue's Downloaded
section asks "Remove from history?" and explains the file itself stays. Verified:
the entry left the registry, the file stayed on the SD card.

**Search is flexible.** Measured on the device: word order, letter case and
diacritics were already free ("kamera wsrod" finds "Z kamerą wśród ośmiornic" —
the index folds accents both ways), and glued-together names now match too
("aerial as" also finds "aerialas.zip" via a second joined-terms query).

**Game info and covers are live ([D21](decisions.md#d21), from the owner's
metadata request).** The detail pane now shows the canonical title and system from
an anonymous hash lookup (hasheous.org) and box art from the libretro thumbnail
server, cached on disk, silent when nothing is found. Verified on the device: a
No-Intro C64 game showed "Real Ghostbusters, The · Commodore 64" with its real
box art. Descriptions would need an API key (measured: those endpoints return
401 anonymously) — left as a future opt-in.

**Release signing is wired and proven.** The build reads `keystore.properties`,
signs the release APK, and the signed build (34 MB → 31.5 MB shrunk) installed
and ran on the device — onboarding came up as it should on a fresh install. The
test used the debug key; the real key is one `keytool` command the owner runs,
after which `keystore/` and `keystore.properties` must be backed up: updates only
install when signed with the same key.

---

## 2026-07-20 — The last M4 box: a downloaded ROM, seen and played in the frontend

The proof came from real use, which beats any staged test. The device's frontend
(Daijishō) syncs its Commodore Amiga platform from the exact folder this app
downloads into (`ROMs/amiga` on the SD card). Its library lists the games the app
placed there, and its own history shows **Mortal Kumquat III — a file this app
downloaded — launched and played by the owner** ("Last played 3 hr. ago. Played
1 times"). Placement → discovery → launch, end to end. **M4 is done.**

One practical note for the owner: the frontend only shows folders it has been given
per platform. Amiga is wired up; an Arcade platform was not visibly configured, so
the arcade test file downloaded today (`progear.zip`, a real playable FinalBurn Neo
game, 13.8 MB, payload uploaded 0 B) sits correctly in `ROMs/arcade` waiting for
one. It was left in place on purpose — remove it if unwanted.

---

## 2026-07-20 — Battery audit and the catalogue-update button, both for real

**Battery (R29 ✅).** Measured on the device around a real 151 MB download. Idle,
the app holds no wakelocks and uses 0% CPU. While downloading it held exactly the
locks a foreground download needs (the system job and the foreground service) at
~15% CPU; ten seconds after the download finished, wakelocks were back to zero and
CPU to 0%. Nothing leaks. One honest gap: milliamp-hour totals cannot be measured
while the device is powered by the USB debugging cable, so the last word belongs to
the battery screen after a day of normal use.

**The update button was finally pressed (R34, fully closed).** The catalogue on the
device predated version recording, so the update banner had never had a reason to
appear; the recorded version was marked stale by hand and the app then did the rest
itself: banner shown → "Update the list" pressed → 1.76 GB index downloaded in
~9 minutes → catalogue rebuilt (2,428,272 files, 1,591 platforms, 521 MB) → the
archive's real version recorded → banner gone. Favourites survived the rebuild and
show their downloaded markers. **M5 is done; M6 now waits only on the owner's
signing key.**

---

## 2026-07-20 — M2 and M3 closed: pause/resume, memory cap, a gamepad keyboard

**Pause and resume are real now (R12 ✅).** The Queue screen's Stop button became a
Pause/Resume pair that follows what the system is actually doing. Measured with a
151 MB file from the MAME torrent: download at ~4.5 MB/s, paused mid-flight (the
engine stopped within a second, the item returned to "queued", 90 MB of fetched
data kept on disk), resumed — the engine rechecked the kept data and continued
**from 50% in under two seconds**. Nothing was downloaded twice. Payload uploaded
0 B through the whole exercise, as always. Test files were removed afterwards.

**Long scrolls no longer accumulate memory (M2 ✅).** The ROM list now keeps at most
1,000 rows in memory and reloads discarded ones from the catalogue when scrolling
back. Measured on the 753,442-row MAME platform: 250 fling-scrolls deep, Java heap
flat at ~16.5 MB before and after; scrolling back up reloaded the earlier rows
without a visible seam.

**Search can be typed with the gamepad (R23 ✅, [D20](decisions.md#d20)).** Measured
first: the system on-screen keyboard ignores every d-pad and confirm key — a pad
user could not type one letter. Search therefore got its own on-screen keys
(a–z, 0–9, space, backspace) drawn by the app, where the pad already works; letters
were typed on the device using pad key events alone, and the system keyboard never
appeared. A second trap fixed on the way: the text field itself swallowed d-pad
presses, so a down-press is now routed out of the field onto the keys.

**Two requirements dropped after measurement, not guesswork:** R33 (own tracker
list — the torrents' trackers are dead and DHT found every live swarm in every
test, [D18](decisions.md#d18)) and R28 (data cap — downloads are unmetered-only,
[D19](decisions.md#d19)). With these, **M2 and M3 are fully done.**

**Game metadata research done** at the owner's request: where titles, descriptions
and cover art could come from (hash-first matching against the catalogue's md5).
Findings and a recommended shape in [metadata.md](metadata.md); nothing is planned
into a milestone yet.

---

## 2026-07-20 — Reboot survival proven, and the owner's review notes addressed

**The queue survives a device reboot (R13 ✅).** Measured end to end: a file was
queued with Wi-Fi off (so it had to wait), the handheld was rebooted, Wi-Fi came
back — and the download ran, verified and placed the file **without the app being
opened at all**. Android's WorkManager woke the worker in the background; payload
uploaded 0 B as always. This was the last untested half of R13; resume after
closing the app was proven earlier.

**Owner's review of M7** (recorded 2026-07-20): the look is accepted for now; the
FinalBurn Neo folder mapping question is postponed; the loose ends went as
recommended:

- Moving files after a folder change now runs in its own background scope
  (`FileMover`) — leaving the settings screen no longer interrupts it.
- The queue screen shows the 20 most recent downloads and says how many more there
  are, instead of growing without bound.
- The `ddrstraw*` test files were removed from the SD card and from the downloaded
  record.

## 2026-07-20 — M7 identity and guidance: the app looks like a product now

The last two M7 packages, both tested on the connected device.

**Identity.** One dark colour scheme built for the handheld ([D17](decisions.md#d17))
replaces the template purple: near-black background, amber for the one action that
matters on each screen, green reserved for "you have this", red for errors. List rows
are separated by hairline dividers and given room to breathe. The active download shows
a real progress bar — captured live on the device while a 496 kB file came down from
the "MAME - ROMs (merged)" torrent (a swarm not tried before — it has seeds and
delivered in seconds; payload uploaded 0 B, as every run).

**Guidance.** Folder rows carry an edit icon, so "you can tap this" is visible instead
of secret. Removing a queued game now asks "Remove from queue?" instead of firing on a
bare tap — the dialog was exercised on the device (Cancel left the item downloading; it
finished and moved to Downloaded on its own). Queue, search and favourites explain
themselves when empty, and the queue's hint says plainly that downloads start by
themselves.

Screens verified by screenshot on the device: collections, platform list, game list
with details pane, search with downloaded markers, queue with the Downloaded section,
settings with edit icons. A second new swarm also proved live along the way: the
FinalBurn Neo torrent (58 kB game, downloaded and placed while the confirmation dialog
was open).

**M7 is now complete in code and tested screen by screen.** What remains is the part
no test can claim: the owner using it on the couch. The "done when" for M7 — a
first-timer finds, downloads and knows it succeeded, by touch alone and by D-pad
alone — is ready for that judgment.

## 2026-07-20 — M7 behaviour: the queue takes care of itself

The second M7 package is done and tested on the connected device. R36–R39 all work:

- **Adding a game starts its download** (R36). Tested live: a game was added from
  the search screen with no "Download" button anywhere — the worker started on its
  own, fetched the 1,867-byte file from the 305k-file MAME torrent and placed it in
  `arcade/`. Payload uploaded: 0 B, as always. Anything still queued when the app
  opens is also picked up automatically.
- **Finished downloads leave the queue** (R37) and appear in a "Downloaded" section
  on the queue screen, with the folder they went to. The queue database migrates old
  finished rows there on first launch (verified: the three old test entries moved).
- **Lists mark what is downloaded** (R37): browse, search and favourites rows show
  "✓ downloaded"; the details pane shows a badge and offers "Download again".
- **A file already in the destination counts as downloaded** (R38). Tested: a file
  matching name and size was put in the destination folder, then "Download" pressed —
  it was recorded as downloaded instantly, nothing was queued, nothing transferred.
- **Changing a folder asks about existing files** (R39). Renaming the mame folder
  from `arcade` to `arcade2` raised "Move 2 downloaded files from arcade to arcade2,
  or leave them where they are?" — "Move files" physically moved the file on the
  SD card and updated the record; tested in both directions. The same question is
  asked before the root folder changes.

Also: the queue's manual "Download" button is gone; "Retry failed" restarts the
worker itself; download runs are chained (`APPEND_OR_REPLACE`) so adding a game
never kills a download already running, and failed items wait for an explicit retry
instead of re-downloading on every run.

**Not yet covered:** moving files runs while the settings screen is open (a very
large library move could be interrupted by leaving the screen); the "Downloaded"
list on the queue screen will grow unbounded — pruning or a dedicated history
screen belongs to the remaining M7 packages.

## 2026-07-20 — M7 structure: the app now has real navigation

The first M7 package (structure) is done and tested on the connected device:

- **A persistent bottom menu** — Browse · Search · Favourites · Queue · Settings —
  on every screen, with icons. Switching tabs keeps each tab's place: leaving the
  FinalBurn Neo platform list for the Queue and coming back lands exactly where
  the user left off (verified on the device).
- **A back arrow with the screen title** on every sub-screen (collection,
  platform, game list).
- **The landscape screen is finally used:** game lists (browse, search,
  favourites) show the list on the left and the selected game's details on the
  right — no more navigating away just to read a size or add to the queue. The
  selected row stays highlighted.
- **The Dev screen is gone from the main UI.** It is now a "Developer tools"
  entry on the Settings screen, present only in debug builds — release users
  will never see it.

**Measured on the device (RG477V):** all five tabs switch correctly by touch;
back arrows return to the right screen; search-by-typing works with results and
details side by side; D-pad focus stays visible while scrolling and reaches the
bottom menu. One fix along the way: the Material icons artifact is no longer in
the Compose BOM (it froze at 1.7.x), so its version must be pinned explicitly.

Remaining M7 packages: behaviour (automatic queue), identity (colours), guidance.

## 2026-07-20 — The owner reviewed the app: it works, but it feels like a prototype

The owner used the working app and set new guidelines: no way back between screens,
no menu, lists blur into a wall of text, nothing marks what is downloaded, tappable
things don't look tappable, and the default template styling looks nothing like a
finished product. The device's touchscreen deserves to be a first-class input next
to the gamepad.

**What was done today:** a UX/UI audit against screenshots from the device — written
up in [design.md](design.md), the new home for design decisions. Five new
requirements (R36–R40): automatic background downloading of whatever is queued,
finished items leaving the queue, downloaded markers in the browse lists (including
files already present in the destination folder), a move-or-leave question when a
folder changes, and the finished-product look itself. A new stage **M7 — user
experience overhaul** plans the work in four packages: structure, behaviour,
identity, guidance.

Stale requirement statuses were also corrected (R3, R5–R7, R10, R11, R14, R35 were
long done and tested, but still listed as planned).

**No code changed today under this heading** — this entry records the review, the
audit and the plan.

## 2026-07-20 — Edge cases: the 305,150-file torrent and zero-byte files

Two edge cases from the hardening list, both tested on the device.

**The biggest torrent in the archive was impossible to download — now fixed.**
"MAME - EXTRAs" holds 305,150 files described by 27.4 MB of torrent metadata. The
torrent library's parser refuses metadata that big out of caution ("bencoded item count
limit exceeded"), so every download from that torrent failed before it began. The
parser's safety caps are now raised to fit this archive. A 10 kB file picked out of
those 305,150 was then downloaded, verified and placed in `arcade/` — checksum matched,
0 bytes of file content shared, and the whole app used ~181 MB of memory at the worst
moment, well within what the device has.

A side discovery: this torrent **does** have a seed — the download ran at over
1 MB/s. The earlier note that "the large MAME torrent finds no peers" was either a
different torrent or a temporary state, and with the parser fixed it can now actually
be retried like any other.

**Zero-byte files work.** The catalogue lists 166 files of size zero (mostly empty
placeholders the archive mirrors faithfully). Two were downloaded — one alongside a
normal file, one entirely on its own. Both completed, matched the empty-file checksum,
and were placed. The lone zero-byte download finished in under a second without
waiting on any peer, which was the case worth checking: a download of zero wanted
bytes could plausibly have hung until the five-minute stall alarm.

**Measured:** `ddrstraw.png` 10,000 B from the 305k-file torrent, MD5
`5b54fef2...` matched; two empty `.chd` files placed with the empty-file MD5
`d41d8cd9...`; payload uploaded 0 B on every run; staging directory cleaned after
each group.

## 2026-07-20 — The app can now tell when its list of games is out of date

The catalogue was imported once and never looked at again. The archive keeps changing,
so the list would quietly drift out of date with no way for the user to notice or fix
it short of clearing the app's data. That is R34.

**What was added.** The catalogue now records which upstream file it was built from.
Opening the collection list makes one small request asking the archive whether that
file has changed. If it has, a line appears: *"The archive has published a newer list
of games."* with an "Update the list" button that rebuilds it.

**A catalogue built before this change says nothing.** It has no recorded version, so
there is nothing to compare — and nagging about an update that cannot be confirmed is
worse than staying quiet.

**Tested on the device, all three cases:**

| catalogue's recorded version | result |
|---|---|
| none (built before this change) | no message — correct |
| different from the archive's | message appears — correct |
| same as the archive's | no message — correct |

The check itself is a single request made when that screen opens, not at startup, so a
slow or missing network never delays anything the user is waiting for.

**Not tested:** pressing "Update the list" and letting it run. That deletes the
catalogue and re-downloads 1.6 GB, and the device already holds a working one. The
rebuild reuses the same import path used at first run, which is tested — but the
forced-rebuild button itself has not been pressed on real hardware, and that should be
said plainly rather than assumed.

**A hidden defect was found and fixed along the way.** `CatalogImporter.kt` contained a
raw zero byte inside a text value — invisible in any editor, and enough to make standard
tools treat the whole source file as binary and skip it when searching. It worked, but
it hid the file from tooling and cost time during this task. Replaced with the written-out
escape, which behaves identically and can be seen. The same mistake had already been
fixed once in another file; this was the second instance.

---

## 2026-07-20 — The shrunk release build works, torrent engine included

Release builds strip out code that looks unused. The torrent engine is reached through
a native library rather than ordinary Kotlin calls, which is exactly the pattern that
gets stripped by mistake — and the failure would only show up on a real download, after
release. So it was tested properly rather than assumed.

**Result: the shrunk build downloads, verifies and places a ROM correctly.** Measured
on the device with shrinking on:

| | |
|---|---|
| `Fantasian (1985)(Xtal Soft)(JP)…zip` | 22,727,819 B, 3 seeds |
| MD5 | matched the archive |
| Landed in | `/sdcard/Download/x1/` |
| File content shared | 0 B |
| Staging afterwards | removed |

**Size.** 43.3 MB unshrunk → 34.4 MB shrunk. Most of the app is the 15.9 MB torrent
library for two processor types, which cannot be shrunk.

**How it was tested.** The release build normally installs as a separate app with an
empty catalogue, and importing 1.6 GB just to test it would be absurd. Instead the
shrunk build was temporarily given the development app's name so it took over the
existing catalogue, queue and folder permission. That change to the build file has been
reverted, and the ordinary development build reinstalled and confirmed running.

**Not proven by this:** the app is signed with a development key. A real release needs a
proper signing key, which is the owner's to create and keep.

---

## 2026-07-20 — Losing Wi-Fi mid-download turns out to be a pause, not a failure

Tested for real: a 54.5 MB download was started and the device's Wi-Fi was switched
off at 67%, then switched back on.

**What happened.** The queue put both items back to "queued" — no error, no lost work.
When Wi-Fi returned, the download resumed by itself from where it stopped and
finished. Both files were verified and placed:

| | |
|---|---|
| `Front Line (1983)(Nidecom Carry Soft)(JP).zip` | 31,798,343 B → `sharp-mz-2000-mz-2200` |
| `Fantasian (1985)(Xtal Soft)(JP)[poss. X1 Turbo only].zip` | 22,727,819 B → `x1` |
| MD5, both files | matched the archive |
| File content shared | 0 B |
| Peak speed | 5.0 MB/s, 7 seeds |

**Why there is no "no connection" message here.** Large downloads only run on an
unmetered network, so when Wi-Fi disappears the system stops the download rather than
letting it fail — and stopping is already handled as "put it back in the queue". The
error message written for lost connections therefore does not appear in this case, and
should not: this is a pause, and calling it a failure would be wrong. That message
still covers failures where the network is present but the archive cannot be reached.

**A real leak was found and fixed while measuring this.** The torrent engine leaves a
scratch file and the torrent's folder tree in the app's private storage after the
wanted files are taken out — 8 MB left over from a single 54 MB download, growing with
every download and never cleaned by anything. Staging is now cleared once files are
placed, but *only* after a successful download: an interrupted one keeps its partial
data, which is exactly what let the test above resume. Verified afterwards: the staging
folder no longer exists once a run finishes.

**Three ROMs have now been placed into three different platform folders**, each
checksum-matched. That is the M4 path working repeatedly, not once.

---

## 2026-07-20 — A failed download now says why, and can be retried

Until now a download that failed showed the word "failed" and nothing else. The user
could not tell whether to wait, free up space, reconnect, or give up on that file.

**Every failure now carries a reason, in plain language.** Nobody is sharing the file;
no connection; not enough space (with the numbers: how much is needed, how much is
free); the downloaded file was damaged; the folder could not be written to. The reason
is stored with the item and shown under it in red.

**"Retry failed" puts everything that failed back in the queue** and clears the old
reason. The button only appears when something has actually failed.

**Space is checked before a torrent is started,** not after gigabytes have been
fetched.

**The queue survives an app update.** The database gained a column for the reason.
The old upgrade path deleted the whole table, which would have silently emptied the
user's queue on the next release — it now migrates instead. Verified on the device:
the queue went from version 1 to version 2 with the existing item intact.

**Tested with a real failure, end to end.** The MAME ROM was queued and downloaded
for five minutes with zero peers, then failed with *"No one is sharing this file right
now — try again later"*, shown on screen. "Retry failed" returned it to the queue and
cleared the message.

**This also settles the open MAME question from two entries ago.** That swarm has now
been watched twice for five minutes with zero peers and zero connections, while two
other torrents from the same archive download in seconds. The torrent has no seeds.
Nothing in the app is broken; the file is simply not available right now — which is
exactly what the new message tells the user.

*Not yet done in M6:* losing the network mid-download is classified in the code but has
not been tested by actually dropping Wi-Fi; the odd edge cases (a 305k-file torrent,
zero-byte files) are untouched; and there is no release build with shrinking yet.

---

## 2026-07-20 — A fresh install now explains itself

Until now the app assumed you already knew three things: that a disclaimer existed
somewhere, that the catalogue had to be imported from a screen labelled "Dev", and
that a destination folder had to be chosen before anything could be saved. None of
them were signposted, and each one silently blocked everything after it.

They are now one numbered sequence, shown automatically whenever setup is incomplete.

**Step 1 — what this app does, and what it does not answer for (R30).** Plain
language: the app finds files in a public archive and downloads what you pick; it
never sends your files to anyone else; what you download and whether you may have it
is your decision, the author hosts nothing and takes no responsibility. It has to be
acknowledged once, and the answer is remembered.

**Step 2 — where ROMs should go.** Explains that only that one folder is accessed,
and that a subfolder per console is created inside it.

**Step 3 — getting the list of games.** Says plainly that it is about 1.6 GB, needs
Wi-Fi, takes a while, and can be left running. Progress is shown as "Downloading the
index — n%" then "Building the list — n%". A failure offers "Try again" instead of a
dead end. When it finishes, the app reopens the catalogue by itself — before, a
freshly imported catalogue stayed invisible until the app was restarted.

**Tested on the device.** Setup was reset and the flow walked through: step 1 →
step 2 → folder granted → straight to the collection list, because the catalogue was
already there. Step 3 was then checked by temporarily hiding the catalogue file and
restoring it afterwards — deliberately, rather than deleting a 521 MB catalogue that
takes hours to rebuild. The screen appeared correctly and the catalogue came back
intact.

**Still open in M5:** the battery audit (R29), and a data-usage limit (R28). On R28 it
is worth saying that large transfers are already restricted to unmetered networks, so
a mobile plan cannot be hit by accident — a separate megabyte cap may be solving a
problem the app no longer has. Worth confirming before building it.

---

## 2026-07-20 — Downloaded ROMs now land in a real folder

Until now a finished download stayed inside the app's private storage, where no
emulator can see it. That gap is closed.

**The user picks one root folder.** The app asks Android's own folder picker for it,
so it gets access to that folder and nothing else — no blanket permission to read the
whole device. The choice survives a restart; verified by force-stopping the app and
reopening it, the folder was still `primary:Download`.

**Each platform gets its own subfolder,** named the way emulator frontends expect.
The names come from the mapping table shipped with the app: of its 1,591 platforms,
507 have a standard folder, and those cover 68% of all files in the archive. A platform with no standard folder keeps a tidied-up
name from the archive instead of being refused — this is requirement R18, and without
it roughly a third of the archive would be undownloadable. Seen on the device: the
MAME platform is offered `arcade`, while `Panic/Playdate` gets `panic-playdate` and is
labelled "no standard folder" so the user knows it is a guess.

**Any of those names can be changed.** Renaming `panic-playdate` to `playdate` on the
device took effect immediately and survived a restart.

**Nothing is moved until the checksum matches.** Files are written to private storage
while downloading and copied out only after verification (R21), so a corrupt or
half-finished file can never appear in a folder the emulator scans. A file that fails
verification is now deleted rather than kept — left in place it would be mistaken for
a finished download next time.

**Measured, end to end.** One ROM, `SynkDate (2023-09-01)(kurogedelic).zip`:

| | |
|---|---|
| Landed in | `/sdcard/Download/panic-playdate/` |
| Size | 41,862 B |
| MD5 on the device | `05b119c37aff439c1417c1d245f7be85` |
| MD5 published by the archive | `05b119c37aff439c1417c1d245f7be85` |
| File content shared with others | 0 B |
| Staging area afterwards | empty |

**Downloading is refused outright if no folder has been chosen.** Fetching gigabytes
and only then discovering there is nowhere to put them wastes the user's bandwidth and
hides the real problem behind a failed item.

*Not yet done in M4:* confirming a placed ROM actually appears and launches in the
emulator frontend. That was tested into `Download`, deliberately, rather than writing
into the owner's real ROM library without asking.

---

## 2026-07-20 — Live progress, and the queue survives being killed

Three things were added and checked on the device.

**You can see what is happening.** The queue screen now shows the torrent being
fetched, the percentage, the speed and how many other computers it is talking to,
updating every second. Confirmed on screen: `Minerva_Myrient - MAME - Software List
CHDs (merged) — 0% · 35 B/s · 0 peers`. Stopping the download makes the line
disappear. This completes requirement R35 apart from the free-space warning, which
was already there.

**Closing the app no longer loses the download.** The app was killed outright while a
download was running. On reopening, the download had already resumed on its own.
Anything that was interrupted mid-flight is put back in the queue rather than left
claiming to be downloading — a row that says "downloading" when nothing is would be a
lie the user cannot act on.

*Not yet tested: a full device reboot.* That needs the handheld to be restarted, which
is the owner's call.

**The unexplained failure from the previous entry is now narrowed down.** A third
torrent was tried: TOSEC Panic, downloaded in about 2 seconds, 0 bytes of file content
shared. So two of three torrents work and the large MAME one is the exception — the
engine is not at fault. Whether that particular swarm is dead or something about that
specific torrent breaks discovery is still open, but it is no longer a suspicion
hanging over the whole download path.

---

## 2026-07-20 — The app downloads, and provably does not share

The queue now actually downloads. Pressing "Download" works through it torrent by
torrent, fetching only the selected files and checking each one against the checksum
the archive published before marking it done.

**Tested on the device, from an empty queue:** two ROMs from two different torrents.

| What happened | Result |
|---|---|
| ROM from the TOSEC torrent (4,547 files, 3.18 GB) | downloaded in ~5 s at 391 kB/s |
| Files written to disk | **exactly one** — the requested ROM (plus a small scratch file) |
| Checksum | matches the archive, verified again independently after the test |
| **File content shared with others** | **0 bytes** |
| ROM from the MAME torrent | failed — no peers found, see below |

### The hard rule is now measured, not promised

The app must never share downloaded files. That is now enforced by four things at
once: incoming connections are refused so nobody can reach the device; the engine is
never allowed to send file content even on connections it opened itself; the torrent
is dropped the instant the wanted files are complete, so it never becomes a source;
and the app **checks the number of bytes of file content it sent and refuses to
accept the download if it is anything other than zero**.

That check caught a real mistake of mine. My first version counted *all* bytes sent,
which includes the small messages any client must send to ask for data — 3.8 kB on a
75 kB download. It reported the rule as broken when nothing had been shared. Measuring
the wrong thing would have made the guarantee useless: it would have failed constantly
and been switched off. It now counts file content only, which is what the rule is about.

### A finding that changed the design

Peer discovery had to stay on. Switching off the peer-discovery network seemed the
safer default, but measured on the device it means **zero peers, forever** — the
public tracker addresses listed in these torrents (58 of them) are dead, so it is the
only way to find anyone who has the files. Being discoverable is not the same as
sharing: nobody can connect to the device, and the engine never sends file content.
See [decision D14](decisions.md#d14).

### Unresolved

One of the two torrents — the large MAME one — found no peers in five minutes and was
given up on, correctly, without blocking the rest of the queue. Whether that swarm is
simply dead or something about very large torrents is at fault is **not established**.
It needs checking against a third torrent before any conclusion is drawn.

Live progress in the queue screen is also still missing: the screen shows states, but
speed and percentage only appear in the notification.

---

## 2026-07-20 — M3 started: a download queue you can fill up

ROMs can now be added to a download queue from their detail screen, and the queue
screen shows what is in it. Nothing downloads yet — that is the next step.

The queue groups its contents **by torrent**, because that is how the files will be
fetched: one torrent is opened once and every wanted file inside it is picked up
together, rather than opening the same torrent once per ROM.

It also shows how much the queue will take and how much space is left, and says
plainly when it will not fit — part of requirement R35.

**Tested on the device:** two ROMs from two different torrents added, shown under
their own torrent headings with a correct total; the app was killed and reopened and
the queue was intact; removing an item updated the totals immediately.

| State | Shown |
|---|---|
| Empty | 0 ROMs · 0 B · 0 torrents · 105.9 GB free |
| Two ROMs, two torrents | 2 ROMs · 72.8 MB · 2 torrents |
| After removing one | 1 ROM · 75 kB · 1 torrent |

**A simplification worth naming:** there is no separate "basket". A ROM the user has
picked and a ROM waiting to download are the same thing, and keeping two lists in
step would only give them a chance to disagree. The queue is the basket.

---

## 2026-07-20 — Favourites

A ROM can be marked as a favourite from its detail screen, and the favourites list
is reachable from the home screen.

**Tested on the device:** marked a ROM, closed the app completely, reopened it — the
favourite was still there and still pointed at the right ROM. Also marked one from
MAME, the largest platform, to check that finding a favourite again does not slow
down on a 753,442-entry platform; the list appeared without a visible delay. That is
an observation, not a timing — the lookup was not measured in isolation.

**The decision that matters here:** favourites are stored as the ROM's place in the
archive (collection / platform / file name), not as its row number. Row numbers are
handed out while converting the archive, so re-importing an updated archive would
renumber everything and every favourite would silently point at a different game.
See [decision D13](decisions.md#d13).

---

## 2026-07-20 — The app can be browsed and searched

The first real screens replaced the developer test screen: a list of collections,
the platforms inside one, the ROMs inside a platform, a detail view for a single
ROM, and a search box. The developer screen is still reachable, one button away.

**Tested on the device, driven entirely by the gamepad:** opening a collection,
opening a platform, scrolling, opening a ROM. Search was typed in and returned
results while typing. No crashes.

| Measure | Result |
|---|---|
| Scrolling MAME (753,442 entries), 1,028 frames | **1 dropped frame** (0.1%) |
| Slowest 1% of frames | 13 ms — under the 16 ms budget for smooth motion |
| Memory after scrolling through ~2,000 entries | 18 MB |

**A mistake worth recording.** On first launch the app jumped straight to the
developer screen, as if no catalogue existed — the catalogue was there. The check
treated "still loading" as "not present". Loading a 521 MB file is not instant, so
those two states must be distinguished; they now are.

**A second one.** Nothing on screen could be activated with the gamepad, even though
the same buttons had worked before. The request to highlight the first row was being
made before that row existed, and the failure was being silently discarded. The
highlight is now requested from inside the row itself, and the error is no longer
swallowed — hiding it was what made this take a second attempt.

**Known limitation:** the list keeps every row it has loaded, so scrolling extremely
far grows memory without bound. Not reachable in normal use — 2,000 rows cost 18 MB
— but it is a real ceiling and is on the roadmap.

---

## 2026-07-20 — Catalogue rebuilt so browsing is fast

**The problem:** before building the browsing screens, the catalogue was tested for
the job. It could not do it. Listing one platform's ROMs in alphabetical order made
the database sort *every* entry of that platform — 753,442 of them for MAME — and it
did that again for every page of results. The same applied to showing how many files
each collection holds.

**The change:** each file now records directly which platform it belongs to, the
per-platform totals are counted once during conversion, and an index keeps the names
of a platform pre-sorted.

**Measured on the device:**

| Action | Before | After |
|---|---|---|
| List collections with totals | 515 ms *(on a fast desktop — worse on device)* | **1 ms** |
| Open a platform, first page | — | **15 ms** |
| Scroll 100 pages (5,000 ROMs) | — | **1,249 ms** (12.5 ms per page) |

**What it costs:** the catalogue grew from 395 MB to 521 MB and conversion from 60 to
80 seconds. Accepted: the device has 100 GB free, and without this the interface
could not scroll smoothly, which is a requirement.

**A second problem found while measuring.** Jumping deep into a list by counting rows
from the start took 313 ms per page on the device — while the same query took 4 ms on
the desktop. Testing only on a desktop would have hidden this completely. Paging now
continues from the last row shown instead of counting from the beginning, which costs
the same no matter how deep the user has scrolled.

**Also fixed:** the app now checks which version of the catalogue it has. An older one
is rebuilt rather than used, because it lacks data the new screens need and would
fail at an unpredictable moment.

---

## 2026-07-20 — M2 started: the gamepad blocker is fixed

**The problem:** since M0, a button highlighted on screen would not react to the
handheld's action button. Without this the app is unusable on the target device, so
it had to be solved before any other interface work.

**The cause was not what it looked like.** It was originally written down as a
focus problem. It was not: focus worked all along. The handheld reports its built-in
controls to Android as an *Xbox controller*, and on that mapping the action button
sends a code that Android does not recognise as "confirm" — it only accepts the
centre of a directional pad, or Enter. The button was focused and simply never told
to activate. Direction keys were unaffected, which is why moving between buttons
always worked and only pressing them did not.

**The fix:** the app now rewrites those two controller buttons into the ones Android
understands, once, before they reach the screen — action → confirm, and the second
button → back. Doing it in one place means every screen built from here on is
gamepad-operable without extra work.

**Tested on the device:** three action-button presses produced three activations,
and the second button closed the screen as expected.

**Worth recording:** the first test appeared to fail. It had not — the injected
button press arrived before the window had taken focus. Repeating it showed the fix
working. A single negative result was nearly enough to send this down the wrong path
a second time.

**Still to confirm:** verified with injected input, not yet with a physical press on
the hardware buttons.

---

## 2026-07-20 — Requirements refined

The project owner clarified several assumptions. Changes:

**Scope narrowed — no legal assessment.** The app is a download helper, nothing more.
It will not label the legal status of individual ROMs, and the infringement-reporting
feature (R31) is dropped: the app hosts and distributes nothing, so there is nothing
to report to it. A single disclaimer of responsibility replaces all of it.
See [decision D10](decisions.md#d10).

**Non-distribution promoted to the hardest rule in the project.** Sharing downloaded
files — directly or indirectly — must be structurally impossible, not merely off by
default. Every future change to the torrent engine has to be checked against this.
[Decision D7](decisions.md#d7) rewritten accordingly.

**Metadata confirmed out of scope.** No descriptions, release years or publishers;
the archive has none and guessing from filenames would show wrong data confidently.
Left as a possible future feature. See [decision D11](decisions.md#d11).

**New requirement R35 — live statistics.** While queueing and downloading, the user
must see speed, progress, queue size and free space, and be warned *before* starting
a download that will not fit.

---

## 2026-07-20 — Documentation set up

Created `CLAUDE.md` and this `docs/` folder so that project state, goals and
progress are readable by anyone, not just by whoever wrote the code.

Documentation is public, so it is written in English.

---

## 2026-07-20 — M1 complete: the catalogue works on the device

**Goal:** get the archive's 2.4 million files onto the device in a form that can be
searched instantly.

### Results

| Measure | Value |
|---|---|
| Archive download | 1.759 GB in ~11 min (~2.5 MB/s) |
| Conversion | **60 s** (~50,000 rows/s) |
| Catalogue size | **395 MB** — 4.5× smaller than the source |
| Entries | 2,428,272 files, 1,049 torrents, 1,591 platforms |
| Search | 19 ms cold, 0–2 ms warm |

The catalogue was pulled off the device and compared against a desktop conversion:
integrity check passed, all counts identical, rows matching byte-for-byte. The
`SynkDate` entry is exactly the file that the M0 test downloaded and verified, so
the two stages join up end to end.

The original 0.8 GB size estimate was **2× too high**; corrected.

### Three findings that changed the design

**Full-text search was silently broken.** Quoting search terms — a natural way to
sanitise user input — turned prefix matching into phrase matching. Typing "pokemon r"
returned **1** result instead of 834. Since input is already stripped to letters and
digits, the quotes were unnecessary; removing them fixed it. Verified against
hostile input (`"; drop table file--`, `***`, whitespace) with no crashes.

**Mapping platforms to emulator folders can never be complete.** 21% of the archive
is not game content at all (93,000 PDFs, uploader dumps). Some real consoles have no
entry in the emulator folder standard either. A fallback is therefore mandatory,
otherwise 31% of the archive becomes unreachable. Recorded as requirement R18.
The distribution helps: the top 200 platforms cover 94% of all files.

**Catalogue conversion deliberately does not resume.** Journalling is disabled for
speed, so an interruption can leave a corrupted file; resuming into it would produce
a silently wrong catalogue — bad checksums, failed downloads. At 60 seconds, redoing
it is cheaper than making resumption safe. Resumption stays where it pays off: the
11-minute download. Range and ETag behaviour was verified directly against the
server.

### Problems hit along the way

- Background work crashed on startup: WorkManager declares its own foreground
  service without a type, which has to be overridden in the manifest.
- Import batches must fit one 2 MB cursor window (10,000 rows). A larger cursor
  refills by re-running the query and skipping rows — quadratic cost.

### Not explained

The first build failed on an SQL string literal that decoded as two replacement
bytes, even though the file on disk is plain ASCII at that spot. Removing the SQL
concatenation made it go away, but the root cause was never confirmed. Recording it
rather than inventing an explanation.

---

## 2026-07-20 — M0 complete: the risky path is proven

**Goal:** answer the question the whole project depends on — can a single file be
pulled out of a large torrent, on the target device?

### Results

Out of 17 files in the test torrent, exactly one was downloaded:

- fetched in **2 seconds**
- checksum matched the archive
- exactly 3 files on disk: the torrent file, the requested ROM, and a small
  scratch file for shared boundary data — none of the other 16 files

The riskiest assumption holds. The project is viable.

### Device measurements (Anbernic RG477V)

Android 14 but **SDK 34**, 8 GB RAM (~6 GB free), 101 GB free storage. The RAM
figure effectively retires an earlier worry about parsing the largest torrent's
27 MB of metadata.

### The bug that cost the most time

The first run found peers but downloaded **0 bytes for over two minutes**. To tell a
dead swarm from our own mistake, the tracker was queried directly: 7 seeders — so
the fault was ours.

The cause was throttling upload speed to 1 byte/s in order to avoid seeding. In
libtorrent that limit also covers protocol overhead, so the client could not even
send requests for data. **Consequence for the design:** "no seeding" must be
implemented by stopping the torrent when it finishes, never by throttling uploads.

### Left unresolved

A button focused via the gamepad **did not activate** with the action button; the
test had to click by coordinates. Gamepad navigation is mandatory on a handheld, so
this became a blocker for M2 rather than a footnote.
