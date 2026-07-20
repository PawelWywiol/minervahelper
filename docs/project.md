# What Minerva Helper is

## The problem

[MiNERVA](https://minerva-archive.org/browse/) is an archive of retro games
(ROMs — see the [glossary](glossary.md)). It holds **2.4 million files** spread
across 1,049 torrents.

The trouble is that those torrents are huge and bundled. To get one NES game with
an ordinary torrent client you have to:

1. work out which of the 1,049 torrents contains it,
2. download the torrent file,
3. find the game by hand in a list that can run to **300,000 entries**,
4. deselect everything else so you do not pull hundreds of gigabytes,
5. move the file into the right emulator folder.

Tedious on a desktop. On a handheld console with a small screen and a gamepad
instead of a mouse, effectively impossible.

## The solution

Minerva Helper does all of that for you:

1. **Search** for a game by name, like using a search engine.
2. **Select** the ones you want.
3. The app finds the right torrents and downloads **only the files you picked** —
   it never touches the rest.
4. It checks that the downloaded file is not corrupted.
5. It files it into the folder for the right console.

All on the device, with no computer in the middle.

## Who it is for

The primary user owns an Android-based retro handheld and wants to load games onto
it without hooking it up to a computer.

Test device: **Anbernic RG477V** running GammaOS (Android 14). Everything is
verified on real hardware, never on an emulator.

One consequence of that choice: the app **must be fully operable with the gamepad**
alone (D-pad and buttons). Touchscreens on these devices are small and awkward, and
some users do not use them at all.

## Project goals

1. **Save bandwidth** — download only the selected files. That is the difference
   between 40 kilobytes and several hundred gigabytes.
2. **Be safe** — verify every file before saving it; encrypted transport only.
3. **Be convenient** — background downloading, a queue, resuming after a restart.
4. **Be light** — run on modest hardware without draining the battery or the
   mobile data allowance.
5. **Never distribute** — the app helps a user fetch files for themselves and
   nothing else. It never shares what it downloads.

The full, checkable list is in [requirements.md](requirements.md).

## What this project does not do

- It is not a general torrent client — it serves the MiNERVA archive only.
- It does not share ("seed") downloaded files — it downloads and stops.
- It is not an emulator — it only delivers files where an emulator expects them.
- It hosts no content — files come from other users on the torrent network.
