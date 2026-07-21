# Minerva Helper documentation

Everything about this project: why it exists, what it should do, how it is built,
and how the work is going. Written in plain language — you do not need to be
a programmer to follow it.

First time here? Start with [project.md](project.md).

## Contents

| File | What you will find |
|---|---|
| [project.md](project.md) | What Minerva Helper is, the problem it solves, who it is for |
| [requirements.md](requirements.md) | What the app must do, with the status of each requirement |
| [architecture.md](architecture.md) | How the app is built — in plain terms, with a flow diagram |
| [design.md](design.md) | UX/UI audit of the working app and the redesign plan |
| [roadmap.md](roadmap.md) | The plan: stages, what is done, what comes next |
| [progress.md](progress.md) | Work log: what was done when, with measured results |
| [decisions.md](decisions.md) | Important decisions and **why** they were made |
| [metadata.md](metadata.md) | Research: where game info and cover art could come from |
| [glossary.md](glossary.md) | Terms explained (torrent, ROM, checksum, SAF…) |

## Project status

The core works end-to-end on the device — browse, search, queue, selective torrent
download, verification, placement into per-platform folders.

**M7** ([design.md](design.md)) made it navigate, behave and look like a finished
product: persistent menu, automatic queue, downloaded markers, dark identity, plus
game covers and canonical titles on the detail screen.

**M8** gave the owner control over the queue: several downloads at once (how many is
a setting), drag the queue into the order you want, pause one game without stopping
the rest, a system icon and name on every row, and text size adjustable from −20% to
+100%.

**All stages are closed.** The release signing key was generated on 2026-07-21 and a
signed release APK was built and verified, so the app is releasable. The owner's one
outstanding task is not code: back up `keystore/` and `keystore.properties` outside
this folder — without them no future build can update an installed app.

Still open, none of it blocking: M7 and M8 want the owner's device-in-hand
acceptance, result filtering (R4) was never started, and the reworked download
notification (R24) needs a look on the device.

Details in [roadmap.md](roadmap.md) and [progress.md](progress.md).
