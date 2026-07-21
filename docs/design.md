# Design — UX/UI audit and redesign plan

Written 2026-07-20, after the owner reviewed the working app and set new guidelines:
the app must be intuitive, legible and pleasant for people of any age and experience,
fully usable by touch **and** by gamepad, and it should look like a finished product —
not a prototype.

This page records what was wrong, what "good" looks like on this device, and the plan
to get there. The work was tracked as stage **M7** in [roadmap.md](roadmap.md).

> **This page is history, not a to-do list.** Everything the audit below complains
> about was fixed in M7 and tested on the device on 2026-07-20 — there is no back
> button missing, no purple template theme, no delete-on-tap in the queue. It is kept
> because the reasoning still explains why the app looks and behaves the way it does.
> For what the app is like now, see [roadmap.md](roadmap.md) and
> [progress.md](progress.md).
>
> Last updated: 2026-07-21 (marked as historical; content unchanged since 2026-07-20)

---

## What the app looked like before M7 (audit)

Based on screenshots taken on the device and a review of every screen. The app was
functionally solid — everything below is about how it presented itself and how it
behaved towards the person using it.

### Navigation

- **There is no way back.** No screen has a back button; the only way to return is
  the device's hardware back, which nothing hints at.
- **There is no menu.** Search, Favourites, Queue and Folders are reachable only from
  the home screen. Moving from Search to the Queue means going home first.
- A **"Dev" button sits in the main row of the app** — an internal testing screen
  presented as a feature.

### Appearance

- The app uses Android's default sample theme: purple buttons on a white screen,
  default fonts, no identity. It looks like the first run of a template.
- **The screen is landscape, the app pretends it is portrait.** Content hugs the left
  edge; roughly 60% of the display is empty white space.
- **List rows blur together.** Plain text with no separation, no icons, no cards —
  a wall of names. Nothing marks which items are already downloaded.
- Progress is shown as raw text ("67% · 1.2 MB/s") with no progress bar.
- The device's own game launcher (Daijishō) is the visual standard the owner lives
  in daily: dark theme, cover art, a persistent top bar, button hints along the
  bottom ("A SELECT · B BACK"). Next to it, this app looks foreign.

### Affordance — can you tell what is tappable?

- On the Folders screen, the rows that change a platform's folder **look like plain
  text**. Nothing suggests they can be tapped — the owner confirmed users do not
  discover this.
- In the Queue, **tapping an item deletes it** — a destructive action on the most
  innocent gesture, with no confirmation and no undo.
- The few explanations that exist are terse afterthoughts ("tap to remove"); most
  screens explain nothing at all.

### Behaviour

- Downloads do not start on their own — items sit in the queue until the user finds
  and presses "Download".
- Finished items stay in the queue forever unless removed by hand.
- The browse lists don't know what has been downloaded: nothing is marked, and a file
  already sitting in the destination folder is happily queued again.
- Changing the destination folder silently strands previously downloaded files in
  the old location.

---

## The plan — all four packages shipped in M7

Four packages, each ending with a test on the device. Visual identity came after
structure — a coat of paint on the old navigation would have been wasted work.
All four were delivered and tested on 2026-07-20; see [roadmap.md](roadmap.md#-m7--user-experience-overhaul).

### 1. Structure — navigation people expect

- A top bar on every screen: back arrow, screen title.
- A persistent bottom menu: **Browse · Search · Favourites · Queue · Settings** —
  every main area reachable from everywhere, by touch or D-pad.
- The Dev screen moves out of sight (debug builds only).
- Correct use of the landscape screen: two panes where it helps (list + details),
  content no longer pinned to one edge.

### 2. Behaviour — the queue takes care of itself

- **Adding to the queue starts the download** (still Wi-Fi-only, still in the
  background). No separate "Download" button to discover.
- **Finished items leave the queue** and are recorded as downloaded.
- **Browse, search and details mark what is already downloaded**, so the user never
  wonders "do I have this one?".
- **Files already in the destination folder** (same name and size) count as
  downloaded — the app will not download them again.
- **Changing a folder asks the question it should:** "Move the files you already
  downloaded, or leave them where they are?"

### 3. Identity — a look that belongs on this device

- A dark-first colour scheme designed for a gaming handheld, replacing the template
  purple. One accent colour, used consistently for the one action that matters on
  each screen.
- Clear list rows: separated, breathing, with icons and status badges
  (downloaded ✓ · downloading with a progress bar · failed with the reason).
- Real progress bars wherever something takes time.
- Typography with hierarchy — what matters is bigger, metadata is quieter.

### 4. Guidance — the app explains itself

- Anything tappable looks tappable: buttons look like buttons, editable rows carry
  an edit icon and a hint.
- Destructive actions are confirmed or undoable, never a bare tap.
- Each screen carries one short line of guidance where a first-time user needs it
  ("Downloads start automatically when you add a game").
- Friendly empty states: what this screen is for and what to do next, instead of
  a bare "Nothing here yet."
- Touch targets comfortable for fingers (48 dp+), while every screen stays fully
  operable by D-pad with a visible focus highlight.

---

## What "premium" means here, concretely

Not decoration. It means: the user always knows **where they are** (top bar), **where
they can go** (bottom menu), **what the app is doing** (progress bars and badges), and
**what will happen if they tap** (affordances). The dark, calm visual identity is the
finish on top of that — matching the device the app lives on.
