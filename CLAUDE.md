# Minerva Helper — project instructions

An Android app for finding ROMs in the MiNERVA archive and downloading **only the
selected files** out of large torrents, straight into emulator folders.

Target device: **Anbernic RG477V** handheld (GammaOS / Android 14).

## Start here, every session

Read **`docs/README.md`** — the documentation index. Then, as needed:

| Question | File |
|---|---|
| What is this project and why? | `docs/project.md` |
| What must the app do? | `docs/requirements.md` |
| How is it built? | `docs/architecture.md` |
| How should it look and feel? | `docs/design.md` |
| What is left to do? | `docs/roadmap.md` |
| What is done and measured? | `docs/progress.md` |
| Why was it done this way? | `docs/decisions.md` |
| What does this term mean? | `docs/glossary.md` |

## Documentation must stay current

`docs/` is public documentation for people — plain language, understandable without
expert knowledge. It is the source of truth for project state, and it is updated
**as work happens**, not at the end.

After finishing any piece of work:

1. **`docs/progress.md`** — add an entry: date, what was done, what was measured,
   what failed. Newest first.
2. **`docs/roadmap.md`** — update the task / milestone status.
3. **`docs/requirements.md`** — update a requirement's status if it changed.
4. **`docs/decisions.md`** — record the decision and its rationale, if one was made.

When the user refines a requirement, update `docs/requirements.md` and note the
change in `docs/progress.md`.

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
- The working directory is **not** a git repository.
- Chat with the user in Polish; all repository content in English.

## Quick technical start

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

JDK 17 is pinned in `gradle.properties` (`org.gradle.java.home`); the build fails
without it.
