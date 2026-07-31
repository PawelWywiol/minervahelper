# Glossary

Terms used across this documentation, explained without assuming technical
background.

### ROM

A copy of a game from an old console or computer, stored as a file. An emulator
reads that file and runs the game. The name comes from the read-only memory chips
inside original game cartridges.

### Emulator

A program that imitates old hardware so its games can run on a modern device.

### ES-DE / emulator folder standard

EmulationStation Desktop Edition, a popular front-end for browsing and launching
emulated games. It expects games in folders named per console (`snes`, `nes`,
`psx`…). We follow those names so downloaded games are found automatically.

### Torrent

A way of transferring files where everyone downloading also shares with others,
instead of everything coming from one server. A `.torrent` file is a small
description: what the files are, how big they are, and how to verify them.

### Selective downloading

The ability to pick individual files from a torrent and skip the rest. This is the
core of the whole project — it turns "download 300 GB" into "download 40 KB".

### Seeding

Sharing files with other users after you have downloaded them. This app does not
seed — see [architecture.md](architecture.md).

### Tracker

A server that tells torrent clients which other users currently have a given file.
Without a tracker, a download may never start.

### Magnet link

A short link identifying a torrent without a `.torrent` file. In this archive the
magnet links contain no tracker addresses; the app relies on DHT instead, which
found a live swarm in every test.

### Checksum (hash, MD5, SHA-1, CRC32)

A short fingerprint computed from a file's contents. Change a single byte and the
fingerprint changes completely. Comparing our fingerprint against the archive's
proves the download arrived intact and unaltered. MD5, SHA-1 and CRC32 are different
methods of computing one.

### Catalogue (`catalog.db`)

Our own database on the device, holding information about all 2.4 million files:
names, sizes, checksums, and which torrent each belongs to. Built once from the
archive's own database.

### Full-text search / FTS

A search technique that finds words inside text quickly, the way a search engine
does — as opposed to scanning every entry one by one. It is why searching
2.4 million entries takes milliseconds.

### SAF (Storage Access Framework)

The Android mechanism by which an app gets access to a folder chosen by the user.
The app cannot simply write anywhere on the device; the user must grant a folder
explicitly.

### Background work / WorkManager

The Android mechanism that lets work continue after the app is closed, and resume
after the device restarts. Downloads rely on it.

### Foreground service notification

The persistent notification Android requires whenever an app does lengthy work in
the background. It exists so that no app can consume battery and data invisibly.

### Browser remote

The app's optional control page, opened in a browser on the same Wi-Fi. The handheld
serves the page itself; the browser only tells it what to do, and downloads still land
on the handheld.

### Pairing code

The six digits the device shows once so a browser can be let in. It is used once, lapses
after two minutes, and stands in for a password nobody would want to type on a D-pad.

### Local network / LAN

The private network your router gives to the devices in your home. The browser remote is
reachable there and nowhere else — no part of it is exposed to the internet.

### D-pad

The directional pad on a gamepad. On a handheld console it often replaces the
touchscreen entirely, which is why the app must be fully operable with it.

### Handheld

A portable games console. Here specifically an Android-based retro handheld, the
Anbernic RG477V.
