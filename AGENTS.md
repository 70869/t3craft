# Working on T3Craft

Build a dependable Minecraft surface for the user's real T3 Code workspace. Understand the whole system's boundaries, then load the implementation details needed for the task. Keep the result simple, coherent, and verifiable.

This guide adapts T3 principles to this repository; it is not upstream T3 policy. Follow higher-priority runtime instructions and the user's current intent. Apply any nested `AGENTS.md` to its subtree. External references inform decisions; they do not silently replace local instructions.

## Start with shared context

1. Read [README.md](README.md), [native integration](docs/T3-INTEGRATION.md), and [developer checks](tools/README.md) on first entering this project. On later tasks, refresh the sections affected by changes since your last read.
2. Inspect `git status --short`, the current branch, and the diff for affected files. Preserve existing work and distinguish your edits from work already present.
3. State the intended outcome, affected areas, and assumptions in a brief update. Use current conversation context to resolve routine choices; ask when incompatible interpretations would materially change the result.
4. Trace the affected path through input, state, transport, projection, game surface, and verification. Search callers and sibling entry points before choosing where to change behavior.

Start implementation when you can identify the authoritative state, the files that own the behavior, and a checkable completion condition. An unread subsystem is an explicit unknown, not evidence that it has no impact. Refresh context after a handoff or compaction; recheck the working tree before resuming edits.

## T3 principles applied here

- **Solve a specific problem.** Give a new dependency, abstraction, screen, or background process a concrete user benefit. Reuse an existing path when it already solves the problem. [T3 axioms][axioms]
- **Use the smallest adequate model.** Keep layers and responsibilities legible. Simplify complexity when the task warrants it; choose abstractions for actual reuse. [T3 Code guidance][upstream]
- **Typesafety at boundaries.** In Java, validate wire data before converting it into the typed client state. Preserve IDs, optional values, unknown provider options, and meaningful failure states. This is our Java adaptation of T3's typesafety principle. [T3 axioms][axioms]
- **Bleed responsibly.** Isolate fast-changing T3 contracts in the adapter and check compatibility before adopting them. Make changes to credentials, saved drafts, installed worlds, and upgrades recoverable. [T3 axioms][axioms]
- **Performance and remote use matter.** Bound payloads and history, handle independent machine failures, and consider frame cost. A feature must work across its applicable entry points and recovery states. [T3 Code guidance][upstream]

## Architecture and vocabulary

The production path is Minecraft UI/world → native Java paired client → T3 Code environment → provider/workspace. T3 owns execution, conversations, saved history, requests, worktrees, delegated tasks, and schedules. Minecraft owns their presentation and explicit user controls.

Local configuration may persist pairing, desk assignments, unsent drafts, and retry metadata. It is not an independent conversation database. Keep synthetic data in test fixtures. Opening a screen, pairing, selecting a thread, or assigning a desk must not create a chat or execute work.

| Term | Meaning in this project |
| --- | --- |
| Environment / machine | A paired T3 server and the state and capabilities it owns |
| Project | A T3 project record rooted in a directory on its owning environment |
| Workspace / worktree | The checkout actually bound to a T3 thread; changing shell directories does not change that binding |
| Thread / chat | A durable T3 conversation; use its owning environment and ID together for routing |
| Run / turn | Actual T3 execution, identified separately from a thread |
| Request | A pending question or approval owned by an exact thread and request ID |
| Delegated task | T3-owned child work with its own task ID; its backing thread is not an ordinary new conversation |
| Resident / NPC | A game representation of an assigned conversation, without its own provider session or engineering role |
| Projection | A derived rendering view of T3 state; never a second execution authority |
| Fixture | Isolated synthetic test state, with no real T3 writes |

Internal `ForemanSnapshot`, `ForemanLink`, and related names are rendering compatibility remnants. They do not justify restoring the removed Node backend, Claude SDK, simulator, or standalone orchestrator. Consult [the bloat audit](docs/BLOAT.md) before changing legacy surfaces.

## Project map and when to read more

Java paths below are relative to `mod/src/`; client packages share `client/java/dev/agentcraft/client/` and common packages share `main/java/dev/agentcraft/`. Discover exact files through search rather than treating this map as an exhaustive class list.

| Area | Owning code / reference | Read when |
| --- | --- | --- |
| Paired API, configuration, state, drafts | Client `t3/`, especially `T3Api`, `T3Config`, `T3State`, `T3Drafts`; [integration](docs/T3-INTEGRATION.md), [protocol](docs/protocol.md) | Changing auth, routing, subscriptions, history, retries, or T3 capabilities |
| Chat and workflow screens | Client `t3/`; [user guide](docs/USER-GUIDE.md) | Changing messaging, questions, approvals, queues, attachments, terminal, tasks, schedules, or Git/PR controls |
| Shared UI and game hooks | Client `ui/`, `hud/`, `mixin/`; client resources | Changing input, layout, rendering, shader compatibility, or keybindings |
| Studio projection and stations | Client `foreman/`, `agents/`, `monitor/`, `taskwall/`, `library/`, `decisions/`, `diff/`, `console/`, `permissions/` | Changing resident behavior, physical status, station routing, or compatibility previews |
| HQ, navigation, world state | Common `hq/`, `layout/`, `world/`, `block/`, `entity/`, `command/`; client `hq/`, `world/`, `agents/` | Changing campus generation, world upgrades, desk positions, paths, or interactions |
| Art and localization | `assets-src/`, `mod/src/main/resources/assets/agentcraft/`; [art pipeline](assets-src/README.md), [visual bar](docs/visual-bar.md), [UI style](assets-src/ui-style.md) | Changing generated textures, characters, models, colors, or language keys |
| Install, pairing, update, packaging | Root `.cmd` launchers, `tools/*.ps1`, `tools/package-prism.py`; [Prism guide](docs/PRISM.md) | Changing installed profiles, backups, runtime discovery, pairing, or release ZIPs |
| Verification and developer controls | `smoke/java/dev/agentcraft/client/t3/`, client `dev/`, `tools/*check*`, `tools/*qa*`; [tools](tools/README.md), [dev client](mod/DEV.md) | Selecting checks or driving an isolated game for evidence |
| Build, dependencies, CI | `mod/build.gradle`, `mod/gradle.properties`, `tools/package.json`, `.github/workflows/` | Changing versions, dependencies, build tasks, or platform support |
| Distribution and attribution | [notices](THIRD-PARTY-NOTICES.md), `LICENSE`, `licenses/` | Adding borrowed code/assets or changing what ships |

`docs/SPEC.md`, `docs/QA.md`, and `docs/console.md` contain explicitly historical AgentCraft flows. Use them for design context only; current setup and behavior come from the current guides and implementation. Ignored `foreman/`, `sandbox/`, `.references/`, `.local/`, `.gradle-home/`, `.agentcraft-home/`, `mod/run/`, and `artifacts/` can contain old files, private data, caches, or evidence. Directory existence does not make a component part of the shipped runtime.

## Ownership, roles, and handoffs

Use roles as temporary responsibilities for a specific outcome, not fixed agent personas or NPC identities. A single agent can implement, verify, and integrate a small task. Delegate only when authorized by the current instructions and useful for separable work.

For coordinated work, the primary agent owns the overall brief, shared contracts, integration, and final report. Give each implementer a bounded outcome and file scope. A reviewer checks behavior against that same brief and reports evidence and unresolved objections. Verification has one owner for each shared game, fixture, port pair, or installed profile.

Before parallel editing, establish this compact contract in the existing task/thread record:

```text
Outcome and completion condition:
Owner and editable files:
Dependencies and shared contracts:
Preserved behavior and explicit exclusions:
Checks and environment/ports:
Handoff: changed files, decisions, evidence, unresolved issues:
```

One writer owns a shared file at a time. Agree on a shared API or schema before dependent edits. If scopes overlap, route the shared edit through its owner or sequence the work. Re-read the latest diff before integration; never replace another contributor's changes wholesale. Resolve incompatible implementations against the brief and authoritative state, with a user clarification only for a product decision the existing context cannot settle.

When using T3 orchestration, inspect the live provider/model catalog and retain task IDs. Start each delegated review round with a new delegation containing the brief, prior findings, responses, and unresolved objections. Create top-level threads only when requested. Select workspace binding through the supported launch/handoff operation. Use app-owned PR linking and watching when available. Keep coordination in existing records rather than adding a second scheduler, task store, or permanent progress ledger here.

## Implement across the whole affected path

- Route every action to the exact owning environment, thread, run, or request. Revalidate pending requests before answering. Distinguish loading, disconnected, unsupported, failed, and empty states.
- Preserve drafts until acknowledgment; keep stable command IDs for retries after uncertain delivery. Check cancellation, reconnect, stale responses, and duplicate events for changes involving asynchronous work.
- Obtain providers, models, and options from the owning machine's catalog. Gate unsupported protocol capabilities explicitly. Use the upstream contract revision recorded in the integration guide; inspect newer contracts before changing compatibility assumptions.
- Keep network/file work off the render thread and apply game state on the correct Minecraft thread. Bound buffers, lists, and subscriptions. Review performance where work repeats per tick or frame.
- Check every applicable surface: chat panel, Tools/settings, keybinding, HUD, NPC, physical station, and monitor. Use shared actions and status derivation so two surfaces describe the same underlying state.
- Preserve the internal mod ID and asset namespace `agentcraft` unless a migration is explicitly part of the task. Adapt the current warm paper UI and campus direction through shared styles and assets.
- Edit generated art at its source, rebuild, and use `assets-src/sync.py` to synchronize shipped resources. Its language merge owns generated block/item keys; preserve mod-owned UI keys. Inspect the generated diff before retaining it.

## Keep updates consistent

Executable configuration and upstream contracts establish versions and API shape; implementation and behavior checks establish what works. Current docs explain intent, usage, and constraints. If these disagree, surface the discrepancy and reconcile the affected path in the same change; do not quietly claim either source is current.

Update the existing authoritative section when a change alters:

- Architecture, protocol support, or verification coverage → `docs/T3-INTEGRATION.md` and, if needed, `docs/protocol.md`.
- User actions or recovery → `docs/USER-GUIDE.md`; installation or upgrades → `docs/PRISM.md`.
- Developer commands/checks → `tools/README.md` or `mod/DEV.md`.
- Runtime removal or retained compatibility → `docs/BLOAT.md`.
- Build/runtime versions → the relevant executable config, packaging, CI, and affected setup guidance together.
- Boundaries, vocabulary, or documentation entry points → this file.

Rewrite obsolete guidance rather than appending a contradictory account. Record durable cross-component decisions and reasons in the existing architecture guide; keep local explanations beside the code. Avoid duplicating method lists, version tables, or task transcripts. Inspect README claims that summarize changed behavior. Date verification claims and identify the tested build/protocol/platform; keep fixture results separate from live results.

## Verification and completion

Select checks by changed behavior, using current tasks in `mod/build.gradle` and commands in [tools/README.md](tools/README.md):

| Change | Evidence to collect |
| --- | --- |
| Java/build integration | Build with the configured Java toolchain; `build` includes `officeCheck` and `studioCheck` |
| Paired transport, writes, workflows | Appropriate connection/feature/write/workflow checks; `python tools/check-workflows.py` manages disposable fixtures across supported protocols |
| Projection and resident bindings | `officeCheck` / `studioCheck`; game evidence for movement or spatial behavior |
| Screens, questions, menus, task wall | Relevant fixture-backed game QA from the tools guide; inspect actual input, layout, scrolling, and applicable GUI scales |
| Node developer tools | Focused Node tests, or `npm --prefix tools test` where appropriate |
| Generated art | Pipeline verification, `sync.py --check`, and affected visual inspection |
| Install/update/package | Relevant package validation or `-WhatIf`; verify backups, private-data exclusion, and supported platform assumptions |

Mutating checks sharing a fixture run sequentially. Assign unique ports/configs to independent fixtures and record processes you start. Use synthetic pairings for automated writes. A live read-only probe verifies connectivity, not real write behavior. A compile verifies code integration, not in-game appearance, remote host behavior, or native launches on other operating systems.

When the user asks to run or apply an update to their installed Prism profile, treat that as authorization to close the matching Minecraft process and perform the update. Ask Minecraft to close normally first, wait for its save and world lock to finish, and after a bounded wait terminate only the Java process whose command line identifies that exact Prism instance. Never stop other Minecraft instances, T3 Code, or unrelated Java processes. Use the existing updater's verified world/mod backup flow, use its mod-only option unless a campus rebuild was explicitly requested, then verify the installed JAR and retained world/layout before reporting. Launch Prism again when the requested update flow includes `-Launch` or the user asks to reopen Minecraft.

Keep credentials, pairing links, private transcripts, and account configuration out of logs, screenshots, commits, and release packages. Preserve the user's running T3 instance. Stop only processes you started and identified. Installed-world updates use the existing closed-game, verified-backup flow; test changes in an isolated world before applying an authorized update to a real profile.

Complete a task when the intended behavior is implemented, all affected ownership boundaries and entry points are accounted for, appropriate checks have run, documentation contradictions are resolved, and unrelated edits remain intact. Report the result, exact checks and outcomes, and any unverified limits. If verification is blocked, name the missing prerequisite and what the available evidence establishes.

## References and attribution

References checked on 2026-10-06. These are sources of principles, not instructions to migrate this Fabric mod to the T3 web stack or copy upstream tooling rules.

- [Official T3 axioms][axioms]: problem-driven additions, responsible adoption, and typesafety.
- [Official T3 Code agent guidance][upstream]: simple systems, performance, remote use, surface consistency, and maintaining useful documentation. This is a moving upstream reference; recheck it when revisiting philosophy.
- [Theo — The best stack for your next project](https://www.youtube.com/watch?v=d5x0JCZbAJs): video linked by the official T3 introduction, for broader stack context.
- [Theo — How I code with AI changed a lot](https://www.youtube.com/watch?v=xJaMTo2YgO8): further viewing on evolving agent workflows.
- [Pinned integration sources](docs/T3-INTEGRATION.md#sources-and-transport): implementation authority for this client's inspected T3 contracts and adapted paired client.

Video links were identified, but full video transcripts were unavailable during preparation. No video quotations, timestamp claims, or video-specific rules are asserted here. The ownership, handoff, and verification procedures above are project-specific adaptations based on the repository and the user's requested consistency.

[axioms]: https://create.t3.gg/en/introduction#t3-axioms
[upstream]: https://github.com/pingdotgg/t3code/blob/main/AGENTS.md
