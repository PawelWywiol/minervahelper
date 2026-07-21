# Decisions

Choices that shaped the project, and the reasoning behind them. Recorded so that
nobody — including a future maintainer — has to guess why something is the way it is.

Each entry: what was decided, why, and what it costs.

---

<a id="d1"></a>

## D1 — Kotlin + Jetpack Compose, one module

**Decision:** build a native Android app in Kotlin with Compose, as a single module.

**Why:** the standard Android stack, so the largest pool of documentation and the
best device integration. A single module keeps the build simple; the project is not
large enough for the overhead of splitting it.

**Cost:** Android only. An iOS or desktop version would be a rewrite.

---

<a id="d2"></a>

## D2 — Torrent engine embedded in the app

**Decision:** embed libtorrent4j rather than handing links to an external client.

**Why:** the whole point is downloading *selected files only*. Delegating to another
app would hand back exactly the manual chore we are trying to remove — and most
Android torrent clients cannot do per-file selection well.

**Cost:** a large native library in the app (~40 MB) and full responsibility for
download correctness.

---

<a id="d3"></a>

## D3 — Convert the archive database instead of using it directly

**Decision:** download the archive's 1.64 GB database once and convert it into our
own 521 MB catalogue.

**Why:** the source is too large and too slow to query on a handheld. Conversion
drops a redundant column, packs checksums compactly and stops repeating identical
folder paths.

**Cost:** an 11-minute first-run download and 80 seconds of conversion, plus a
temporary 2.1 GB of disk during the process. Source deleted afterwards.

---

<a id="d4"></a>

## D4 — Downloading resumes, conversion does not

**Decision:** the archive download resumes from where it stopped; catalogue
conversion always starts from scratch.

**Why:** conversion runs with database journalling off, which is fast but means an
interruption can leave a corrupted file. Resuming into a corrupted catalogue would
produce silently wrong data — wrong checksums lead to failed downloads, and the user
would have no idea why. At 80 seconds, repeating the work is cheaper than making
resumption safe. The 11-minute download is a different matter, so it resumes
properly.

**Bonus:** starting clean also handles re-importing an updated archive.

---

<a id="d5"></a>

## D5 — Platform mapping is deliberately incomplete

**Decision:** where a platform cannot be mapped to a standard emulator folder, fall
back to a cleaned-up name from the archive, which the user can override.

**Why:** 21% of the archive is not games — technical documentation, scans, uploader
dumps. On top of that, some genuine consoles (for example Xbox 360) have no entry in
the emulator folder standard we follow. Forcing a complete mapping would make 31% of
the archive unreachable.

**Cost:** some ROMs land in generically named folders. Mitigated by the
distribution: the top 200 platforms already cover 94% of all files.

---

<a id="d6"></a>

## D6 — "Verification" means integrity, not virus scanning

**Decision:** every file is checked against the checksum published by the archive
before it is saved. We do not scan for malware.

**Why:** a checksum proves the file is bit-for-bit what the archive says it is, which
is what actually protects against a corrupted or tampered download. Genuine malware
scanning would require an antivirus engine and a signature database — neither is
practical inside the app, and Android will not execute a downloaded ROM as a program
anyway.

**Cost:** the original brief asked for protection against "malicious software". This
is a narrower guarantee, and it is stated plainly rather than overpromised.

---

<a id="d7"></a>

## D7 — The app never shares files, and that is structural

**Decision:** downloaded files are never made available to anyone else, directly or
indirectly. Seeding is not a setting that happens to be off — it must be impossible.
In practice this means stopping the torrent as soon as the download finishes.

**Why:** this app is a download helper, not a distribution tool. Anything that let a
user share content — even accidentally, even as an option buried in settings — would
change what the app *is* and what its author is answerable for.

**Implementation trap:** the obvious approach, throttling upload speed to near zero,
does **not** work. In libtorrent that limit also covers protocol overhead, so the
client cannot even send requests for data; the download stalls at zero bytes while
still appearing connected. This cost two hours of diagnosis in M0. Stopping the
torrent is the correct mechanism; throttling is not.

**Cost:** we take from the torrent network without giving back, which is poor
etiquette in ordinary use. Accepted deliberately — the rule above outranks it.

**Consequence:** every change to the torrent engine must be re-checked against this
rule. It is the one requirement where a regression would be more than a bug.

---

<a id="d8"></a>

## D8 — Large transfers over Wi-Fi only

**Decision:** the catalogue download, and by default ROM downloads, run only on an
unmetered network.

**Why:** the initial download alone is 1.64 GB. Letting that hit a mobile data plan
by accident would be indefensible.

**Cost:** no first run without Wi-Fi.

---

<a id="d9"></a>

## D9 — Public documentation in English

**Decision:** everything in the repository is written in English; conversation with
the project owner stays in Polish.

**Why:** the documentation is public, so it should be readable by the widest
audience.

---

<a id="d10"></a>

## D10 — No legal assessment of individual ROMs

**Decision:** the app does not judge, label or check the legal status of individual
files. It states once that the user is responsible for what they download.

**Why:** the archive carries no rights information, so any per-file label would be
guesswork presented as fact — worse than saying nothing. The app's role is narrow:
it helps a user fetch files for themselves, and it never distributes anything
([D7](#d7)). What that user chooses to fetch is their decision.

**Also dropped:** a copyright-infringement reporting feature. The app hosts no
content and shares none, so there is nothing for it to receive reports about.

**Cost:** users get no guidance on what is safe to download. Deliberate — pretending
to offer that guidance would be the bigger failure.

---

<a id="d11"></a>

## D11 — No descriptions, release years or publishers

**Decision:** the ROM detail screen shows only what the archive actually contains:
name, size, platform, checksums, source torrent.

**Why:** the archive holds no metadata beyond that. It could be partly guessed from
filename patterns like `(1993)(Coktel Vision)`, but those conventions are
inconsistent across collections, so a meaningful share of entries would be wrong —
and a wrong release year shown confidently is worse than a blank field.

**Future option:** enrich the catalogue from an external metadata source. Out of
scope for now; not needed for the app to do its job.

---

<a id="d12"></a>

## D12 — A bigger catalogue in exchange for a usable one

**Decision:** store extra data in the catalogue — which platform each file belongs to,
how many files each platform holds, and a pre-sorted index of names — even though it
grows the catalogue from 395 MB to 521 MB and conversion from 60 to 80 seconds.

**Why:** without it, opening a platform made the database sort every entry it contains
(753,442 for the largest) and repeat that for every page. Browsing is not an optional
extra; if it stutters, the app fails at its main job. The device has 100 GB free, so
126 MB is a cheap price.

**Related:** paging continues from the last row shown rather than counting rows from
the start. Counting cost 313 ms per page deep into a list on the device — and only 4 ms
on a desktop, so this would have gone unnoticed without testing on real hardware.

**Cost:** a first run now takes about 20 seconds longer.

---

<a id="d13"></a>
## D13 — Favourites point at a place in the archive, not a row number

**Decision:** a favourite is stored as collection / platform / file name, and the ROM
is looked up by that path when the list is shown. It is not stored as the catalogue
row number, even though that would be smaller and faster.

**Why:** row numbers are assigned while converting the archive, in the order files are
read. Re-importing an updated archive renumbers everything. Favourites stored as row
numbers would still open — silently showing different games. A wrong answer delivered
confidently is worse than a slow one.

**Cost:** finding a favourite again is a search by name rather than a direct jump, and
if the archive ever renames a file, that favourite is lost rather than wrong. Losing it
is the better failure: it is visible.

---

<a id="d14"></a>
## D14 — Peer discovery stays on; sharing is blocked at the connection instead

**Decision:** the app keeps using the peer-discovery network (DHT) to find who has the
files, rather than switching it off for safety. Sharing is prevented by refusing all
incoming connections, never permitting file content to be sent, and dropping the
torrent the moment the wanted files are complete.

**Why:** switching discovery off was tried first. Measured on the device, the archive's
torrents then find **zero** peers — the 58 public tracker addresses inside them no
longer answer, so discovery is the only route to a seed. An app that shares nothing but
also downloads nothing is not a safer app; it is a broken one.

**The distinction that makes this acceptable:** being findable is not sharing. Another
user can learn this device is interested in a torrent, but cannot open a connection to
it, and never receives file content. The app verifies this after every download and
refuses the result if a single byte of file content went out.

**Cost:** the device is visible to the swarm while downloading. If that ever becomes
unacceptable, the alternative is to obtain files over plain HTTPS instead — which the
archive does not currently offer.

---

<a id="d15"></a>
## D15 — The destination is a folder the user grants, not a path the app picks

**Decision:** the user chooses one root folder through Android's own folder picker, and
the app writes only inside it. Downloaded files are then copied there, one subfolder per
platform.

**Why:** an emulator's folders belong to the emulator, not to us. The alternative —
asking for permission to read and write all files on the device — is a far larger demand
than "put ROMs where I tell you", and it would have to be justified to the user on a
screen where the honest answer is "so we can write into one folder". The picker grants
exactly that one folder, and Android remembers the grant across restarts.

**Cost:** files must be copied out of private storage rather than moved, so a large ROM
is briefly written twice. Copying is also the reason verification happens first: only a
file that has already matched its checksum is worth the second write.

---

<a id="d16"></a>
## D16 — An unmappable platform gets a guessed folder, not a refusal

**Decision:** where a platform has no folder in the emulator standard, the archive's own
name is cleaned up and used, and the screen marks it as "no standard folder".

**Why:** 507 of the archive's 1,591 platforms have a standard folder, covering 68% of
files. Refusing the rest would make a third of the archive undownloadable to protect the
user from a folder name. A ROM in an oddly named folder is still playable — the user can
rename the folder, and the app remembers it.

**Cost:** the default is sometimes wrong, so the screen has to admit which names are
guesses rather than presenting all of them with equal confidence.

---

<a id="d17"></a>
## D17 — Dark-first, one amber accent, green only for "you have this"

**Decision:** the app ships one dark colour scheme designed for the handheld, replacing
the template's light purple. Amber marks the single primary action on each screen; green
is reserved for the downloaded state; red for errors. No light theme for now.

**Why:** the app lives between launchers and emulators that are all dark — next to them
a white screen with purple buttons read as a template demo, which the owner's review
called out. One accent keeps the eye on the action that matters; a green that means only
"downloaded" makes the markers scannable at a glance.

**Cost:** users who prefer light themes have no option yet. Added only if asked (see the
roadmap's open questions).

---

<a id="d18"></a>
## D18 — No shipped tracker list: DHT is the peer-discovery mechanism

**Decision:** requirement R33 (ship our own torrent tracker list) is dropped. The app
relies on DHT, which was already on.

**Why:** the concern behind R33 was that the archive's magnet links carry no trackers.
In practice the app never uses magnet links — it downloads full `.torrent` files, and
those do list trackers, but measurement showed all of them dead. Every successful
download so far (M0 through today) found its peers over DHT alone, usually within
seconds. A bundled tracker list would add announce traffic to third-party servers
while changing nothing.

**Cost:** if DHT were ever blocked on a network, there is no fallback. Accepted: the
target device lives on a home Wi-Fi.

---

<a id="d19"></a>
## D19 — No data caps or speed limits

**Decision:** R28 (data usage limit) is dropped, and the M3 "transfer limits" idea
with it.

**Why:** downloads only ever run on unmetered networks (enforced since M1), so a
mobile plan can never be touched — which was the only real risk a data cap would
guard against. A speed limit had no user-visible purpose either: the queue downloads
one torrent at a time, and since today the user can pause it at any moment (R12).
Each dropped setting is one less screen to explain.

**Cost:** none identified. Revisit only if a real situation demands it.

---

<a id="d20"></a>
## D20 — Search gets its own on-screen keys

**Decision:** the Search screen shows a small built-in keyboard (a–z, 0–9, space,
backspace) that works with the d-pad. The system keyboard still appears for touch
typing.

**Why:** measured on the device: the system on-screen keyboard ignores the d-pad
completely — no arrow or confirm key does anything — so a gamepad-only user could
never type a single letter, breaking R23 ("no touch required"). This cannot be fixed
from inside an app; the only reliable answer is keys the app itself owns, which
already obey the pad. A second trap was found on the way: the text field swallows
d-pad presses to move its cursor, so a down-press is explicitly routed out of the
field onto the keys.

**Cost:** a strip of the Search screen is spent on keys that touch users do not need.

---

<a id="d21"></a>
## D21 — Game info and covers: keyless sources only, silence over errors

**Decision:** the detail pane enriches a ROM from exactly two sources, both free and
requiring no account: an anonymous hash lookup at hasheous.org (canonical title and
system) and cover art from the libretro thumbnail server (keyed by game name). Every
result — hit or miss — is cached on the device, one lookup per ROM ever. When nothing
is found, or the device is offline, the pane silently shows only what the archive
knows; enrichment may never produce an error the user has to deal with.

**Why:** the alternatives all require credentials — ScreenScraper wants a per-user
account, IGDB direct needs a server secret, and even Hasheous's richer proxy endpoints
(descriptions) turned out to demand an API key when measured. Metadata is decoration
here, not function, and decoration is not worth a login screen.

**Cost:** no game descriptions for now (would need an API key — a future opt-in), and
covers miss when a file name differs from the thumbnail server's naming (TOSEC's
decorated names often miss where No-Intro's clean names hit).

---

<a id="d22"></a>
## D22 — Machine silhouettes in vendor colours, not console logos

**Decision:** a row's icon is two independent facts. The **shape** is what the machine
is — gamepad, modern gamepad, joystick, handheld, arcade cabinet, computer — and the
**colour** is who made it: Nintendo red, PlayStation blue, Sega cyan, Atari orange,
Commodore violet, Sinclair pink, NEC teal, SNK gold, Microsoft green, and a neutral
slate for everything with no single vendor behind it. Content that is not a game at
all — scans, images, video, audio, archives — is identified by its **file type**
instead, in one grey, and is recognised from the file's extension.

**Why the split:** neither axis alone identifies anything. Colour alone cannot tell a
Game Boy from an NES; shape alone cannot tell a Mega Drive pad from a Famicom pad at
24 dp. Together they do both, and a Sega arcade board can honestly read as "arcade
cabinet, in Sega's colour" rather than being forced to pick one.

**Why not logos:** the obvious sources do not survive inspection. The two
comprehensive collections on GitHub (`HVR88/Monochrome-Gaming-Logos`,
`Jaffacakelover/emulator-icons-svg`) carry **no licence at all** and consist of
manufacturer trademarks; the one MIT-licensed set (`scurker/gamicons`) is nineteen
low-resolution images last touched in 2015. None comes close to covering this
archive's 1,591 platforms. A machine's outline plus the vendor's colour is
recognisable without borrowing anyone's mark, and costs about five kilobytes.

**Two colours deliberately are not the brand value.** Sega's `#117BBD` sits close enough
to PlayStation blue to be confusable in a mixed list, so it moves to the cyan its
Dreamcast era owns anyway; Atari's `#D21404` is a second red beside Nintendo's, so it
moves to the orange of the woodgrain 2600. Nintendo's `#E60012` and Microsoft's
`#107C10` are used, but lightened — at their published values they sink into the `#14161A`
background. Fidelity to a swatch nobody sees side by side is worth less than telling
the families apart.

**Why roughly a dozen colours and not one per system:** past about a dozen, colour
stops being information and becomes noise. The vendors a user actually recognises get
their own; the long tail shares a neutral slate and is distinguished by shape. The
row always spells the system out in text — colour is the second cue, never the only
one.

A trap found on the device: whole collections map to a single folder, so FinalBurn
Neo's `megadrive` platform read "Arcade" on a screen titled "megadrive". The
platform's own key now wins over the collection's slug for naming and icons, while
placement still uses the slug — display and folder are deliberately allowed to differ.
Verified again after this change: a Mega Drive game inside FinalBurn Neo shows Sega
cyan, and still lands in `arcade`.

**Cost:** two Mega Drive games look alike at a glance, and a Mega Drive game sits
beside a Master System game in the same cyan. The row text carries the exact system,
which is the part that actually identifies it.

---

<a id="d23"></a>
## D23 — One torrent session for the whole run, staging per torrent

**Decision:** downloading several torrents at once shares a single libtorrent session,
and each torrent stages its files in its own folder.

**Why:** the engine previously opened a session per download, which was invisible
while only one ran at a time. Running several that way would start a separate DHT node
each — peer discovery is the expensive part on this device, and this archive depends
on DHT because the torrents' own trackers are dead ([D18](#d18)). Staging was worse
than merely wasteful: every download wrote into one shared folder that was deleted on
success, so one finishing would have wiped a neighbour's half-finished data.

The non-sharing rule is unaffected and was re-checked rather than assumed: uploaded
payload is counted per torrent handle, so the "must be exactly 0" test still means what
it meant. The handle is now removed on every exit path, not only on success — with a
session that outlives the download, a handle left behind would be a seed.

**Cost:** a fault in the shared session would affect every running download rather
than one. The limit is capped at four for that reason, and because past three the
device spends more time finding peers than fetching files.
