# Minerva Helper — project instructions

An Android app for finding ROMs in the MiNERVA archive and downloading **only the
selected files** out of large torrents, straight into emulator folders.

Target device: **Anbernic RG477V** handheld (GammaOS / Android 14).

## Start here, every session

Read **`docs/README.md`** — the documentation index. Then, as needed:

| Question | File |
|---|---|
| What is this project and why? | `docs/project.md` |
| How is it built? | `docs/architecture.md` |
| What does this term mean? | `docs/glossary.md` |
| How do I install, use and build it? | `README.md` (repo root) |

## Documentation must stay current

`docs/` is public documentation for people — plain language, understandable without
expert knowledge. It describes **how the app works and how to build it**, nothing
else. The development history (roadmap, work log, requirement tracking, decision
record) was removed on 2026-07-21 now that the app is finished and public; it is
still recoverable from git history if ever needed.

Do not reintroduce progress logs or status tables. After finishing a piece of work,
update `docs/` only where the described behaviour or build actually changed, and the
root `README.md` if what the user sees changed.

## Hard rule — the app must never share files

Downloaded files must never be made available to other users, **directly or
indirectly**. This is not a preference or a default setting: seeding must be
structurally impossible. Any change touching the torrent engine has to be checked
against this rule.

## Working rules

- Every milestone ends with a **test on the connected device**, not just a
  successful build. Report real measurements, never claims.
- Minimal code. No superfluous abstractions.
- **No unnecessary comments.** Code must read clearly on its own; comment only
  where intent genuinely cannot be expressed in the code — a non-obvious constraint,
  a trap, a "why", never a restatement of what the line does.
- Use the context7 MCP and web search freely to find the best solution for a given
  feature — prefer current documentation over memory.
- Never create commits and never push. When work is done, propose a branch name
  and a commit message (git-flow + conventional commits, max 70 chars each).
- Chat with the user in Polish; all repository content in English.

## Quick technical start

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

JDK 17 is pinned in `gradle.properties` (`org.gradle.java.home`); the build fails
without it.
