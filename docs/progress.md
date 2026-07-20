# Work log

What was done, when, and what it measured. Newest first.

Each entry records what actually happened — including what failed and what is still
unresolved. Claims without measurements do not belong here.

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
