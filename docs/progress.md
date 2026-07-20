# Work log

What was done, when, and what it measured. Newest first.

Each entry records what actually happened — including what failed and what is still
unresolved. Claims without measurements do not belong here.

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
