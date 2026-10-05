# T3Craft studio feature map

The studio is a client-side view of paired T3 Code history. T3 owns execution, conversation storage, models, permissions and workspaces. Read [the user guide](../docs/USER-GUIDE.md) for controls and [coverage](../docs/T3-INTEGRATION.md) for advanced native gaps.

## Modules and responsibilities

| Module | Responsibility |
| --- | --- |
| `client.t3.T3Api`, `T3Socket`, `T3Protocol` | Supported paired-client authentication, protocol negotiation, bounded reads, HTTP/RPC and event subscriptions |
| `client.t3.T3State` | Per-machine mirrors and health, exact-owner routing, request revalidation, focused history and bounded resident enrichment |
| `client.t3.OfficeRoster` | Six global desks, pin priority and saved per-world slot identity |
| `client.t3.StudioProjection` | Read-only all-chat task cards, history catalog, feed and per-resident monitor logs |
| `client.t3.StudioReplies` | New completed assistant replies; suppress history, duplicate, offline and reassignment replay |
| `client.foreman.T3Projection` | Adapt real T3 state to the original rendering structures and cast; never execute agents |
| `client.agents` | NPC navigation, seats, typing, waiting approach, speech, exclamation marks and transition effects |
| `client.taskwall` | Physical wall layout and hit tests; exact-chat cards and native four-lane board |
| `client.monitor` | Bound T3 chat messages/tool receipts, shared feed, read-only world display |
| `client.library` | Physical saved-chat shelves/catalog; native history browsing |
| `client.decisions` | Real T3 request projection and podium; native request screens own all responses |
| `client.diff` | Physical known-checkpoint cards; native file/hunk review and feedback |
| `client.hq` | Per-agent offline lighting, machine-health slots, studio totals, beacon and reachability QA |
| `client.hud` and `client.t3.T3Hud` | Connection status, actual run notifications and current waiting counts |
| `hq`, `layout`, `block`, `entity` | Original studio builder, anchors, decorations, block entities and client-only cast |
| `client.dev` | Opt-in application QA bridge; disabled in the installed profile |

## Station contract

Right-click handlers live in `client.world.StationInteractions`. Characters and monitors open the linked chat's native card. Task cards preserve the exact T3 thread ID. Empty wall areas open the paginated board. Library items open that chat's saved history; catalog blocks open the full library. Checkpoint cards open the displayed chat's review; empty stations open the review catalog. The decision podium opens all actual requests. Chat terminals open the native composer. Status lamps dispatch to their related desk, decisions, reviews, connections or task board.

Test-bench `ci:#N` bindings are retained for existing world compatibility and display the Nth paired machine's connection health in T3 mode. They do not claim a CI result. Completed chats likewise do not claim passing tests or merged code. Activity exposes actual command outcomes.

The atrium shows the focused chat and studio totals, without an invented goal percentage. Decorative blocks, lounge/meeting seats and building controls keep their original purpose. Six desks are shared across machines; all loaded unarchived chats remain available through the board, catalog, sidebar and decision desk.

## Thread and write rules

Renderers and Minecraft screens use the client thread. Network work uses `T3State`'s executor; immutable snapshots and detail-map copies cross that boundary. Resident background history reads are capped at one desk per maintenance tick, using a 4-second interval for working/waiting chats and 30 seconds for settled chats. A failed read is delayed so it cannot starve other desks.

Pairing, opening screens, NPC interaction, pinning and reading history create no T3 conversations. Only explicit New chat plus a first send creates one. Replies, stop, settings, approvals, question answers and review feedback route to the owning environment and recheck current state. Requests without resident NPCs remain visible at the podium without assigning them to another character.

The old `ForemanState`/`Protocol` names are rendering compatibility structures. `ForemanLink` is disabled; several legacy classes remain referenced by historical developer commands. Standard screen aliases and ordinary game interactions now open native T3 screens. See [remaining bloat](../docs/BLOAT.md).

## Verification

Use Java 25 and `gradlew -p mod build`. `officeCheck` covers capacity and persistence; `studioCheck` covers projections and reply replay suppression. `connectionCheck`, `featureCheck` and `writeCheck` exercise the production paired client against isolated fixtures. `nativeProbe` reads an explicitly paired real environment and sends no prompts.

`node tools/studio-check.mjs 27879` drives fixture-only NPC transitions in a running development game. It checks typing at the character's own desk, exact-request waiting, completion effects, lounge return, new-reply speech, failure, per-machine offline lamps, reconnect, native screens and navigation. Evidence is kept under ignored `artifacts/qa` and `artifacts/shots`.

Art source remains in `assets-src`; generated block labels and key bindings must agree with shipped assets. Keep licenses and original mod/block IDs for world compatibility. No runtime Foreman or Node service is required.
