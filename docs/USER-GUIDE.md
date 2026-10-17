# Using T3Craft

T3Craft brings your existing T3 Code chats into Minecraft and gives them room to breathe. The HQ is a window into T3's state: chat messages, working agents, questions, approvals and completed changes come from your paired T3 machines. The campus shows the work; T3 remains in charge of it.

## First launch: open the doors

Download the recommended Prism import ZIP from the [latest release](https://github.com/70869/t3craft/releases/latest). In Prism choose **Add Instance → Import**, select the ZIP, sign into your Minecraft account, and launch **T3Craft 26.3 Native** with T3 Code running. The first install needs internet for Minecraft, Fabric and the Java runtime. Source builds are optional. [The installation guide](PRISM.md) covers the Windows installer and updating an existing instance.

Connections opens automatically the first time you enter a world while unpaired in each game session. Create a pairing link in **T3 Settings → Connections**, copy the complete link, and paste it into Connections. Give the machine a recognizable label. The link is exchanged for a paired-client credential; a used or expired link needs replacing. You may dismiss the form and reopen it with backtick or `/t3 connections`.

The optional source installer can pair automatically to a running local T3 desktop app using that app's supported pairing CLI. Pairing and setup never create chats or send messages. Existing credentials are preserved on repeat installs.

Keep T3 Code running. The Minecraft Microsoft login and T3 pairing are separate.

The first panel opening after pairing offers **Getting started**: connect a machine, choose an existing conversation, pin its desk, and open its composer. Each step is an explicit control; the guide sends only when you choose Send in the composer. Reopen it from **Your work → Getting started**, **Settings → General**, or `/t3 guide`.

## Find your footing: menu scale and sidebar

T3 menus use a compact desktop layout independently of Minecraft's saved GUI scale. At 1920×1080, GUI settings 2, 3 and 4 all render T3 menus at scale 2; closing the menu restores the world HUD's chosen scale. Smaller windows adapt to the available space.

Search and New sit at the top of the sidebar. **Filter** reveals machine, project and status controls; click a scope to cycle it. **Working** and **Settled** have separate collapsible shelves and scroll independently. Settled follows T3's explicit settled state; finishing a run alone does not settle a chat. Searches include every shelf, and selecting a thread brings its row into view. The selected row has an accent marker, while state colors distinguish working, waiting, failed, done and idle chats.

## Your controls

| Action | Control |
| --- | --- |
| Open chat panel | **Backtick** (`) or `/t3` |
| Open outstanding decisions | **J** or `/t3 decisions` |
| Find waiting, active, assigned or recent work | **Your work** in Chat, the HQ directory, or `/t3 work` |
| Switch between chats in the current list | **Ctrl+Tab**, or **Ctrl+Shift+Tab** to go back |
| Inspect a character's linked T3 chat | Right-click the character, or right-click its monitor and choose Open chat / board |
| Rename a character | Right-click the character → **Desk → Rename NPC** → enter a name → **Save** |
| Open the task board | Right-click an empty wall area, or `/t3 board` |
| Inspect a wall card | Right-click that card; **Chat**, **History**, **Activity**, **Review** and **Decisions** act on its chat |
| Turn a physical wall lane's page | Right-click the left half of its bottom **< page/pages >** strip for previous, or the right half for next |
| Open saved conversation history | **Workspace → History** in Chat, the library station, or `/t3 library` |
| Review completed changes | **Review**, `/t3 review`, or the diff station |
| Change title or modes | **Workspace → Chat settings** in the selected chat |
| Manage paired machines | **Connections** or `/t3 connections` |
| Pin a chat to an HQ desk | **Pin desk**; manage with **Pins** |
| Stop the selected chat's run | **Stop** or `/t3 stop` |

Rebind the panel and Decisions keys in Minecraft's Controls if another mod uses them. Escape closes a panel and preserves its unsent draft across game restarts.

## The history is real

Use the sidebar to choose a chat. Scroll its list and use the machine and project filters to find the right conversation. The status filter cycles **All chats**, **Needs you**, **Active runs**, **At desks**, **Recent chats** and **Failed runs**. Search accepts chat titles, projects and machine names. Waiting requests appear first in the normal list; Recent chats follows T3's update timestamps. Search and filters are retained when you return to the same selected conversation from Workspace or Your work. Opening a different conversation from a resident clears the old filters so that chat remains reachable.

**Your work** gives the same status views in a paged list with a next action beside each conversation. **Answer request** opens its exact current question or approval, **Review changes** opens a known ready checkpoint, and **Reconnect machine** opens Connections. Offline chats remain browsable with their last known status. The conversation header and resident menu use the same next-action rule. A request takes priority over a checkpoint; a running conversation stays focused on its current run.

The conversation panel shows recent T3 messages with basic Markdown formatting. **Workspace → History** reads the saved timeline; **Load older** fetches the next older page, and the mouse wheel scrolls it.

The sidebar includes unarchived chats. **Workspace → Archived chats** reads and restores archived conversations. Drafts, focused chat and machine credentials are saved in Minecraft's private `config/t3craft.json`; desk pins and character preferences belong to the current world. The mod does not import raw Codex or Claude history directories.

Only environments that you explicitly pair supply history. Removing a machine removes its chats from this mod. T3 remains the owner of the conversation history.

## Send the next line

Select an existing chat, type in the composer, and press **Enter**. The message goes to that same T3 chat, using its project, workspace, model and modes. Enter sends and returns to the world; **Shift+Enter** sends and keeps the panel open.

If that chat already has an active run, the new message queues after it by default. **Queue / Steer** chooses whether to queue the reply or steer that exact active run.

Sending keeps the draft until acknowledgment. After an uncertain delivery, the saved retry keeps its original model and Queue/Steer mode; those controls lock until you retry or edit the draft. Editing starts a new command, so first check the conversation if the original may have arrived. Approval buttons show **Waiting for T3** during delivery, prevent duplicate responses, and show a **Response accepted** toast after acknowledgment. Question dialogs retain their own received/failed state.

The model picker reads enabled, ready providers and their models from the selected machine. Picking a model takes effect on the next send and updates that thread's model. Some providers require a new chat to change model; those choices are labeled accordingly and require clicking **+ New** first.

## Start a new chat on purpose

1. Select a conversation in the T3 project and workspace you want.
2. Click **+ New** beside the chat list.
3. Choose a model if needed, write the first message, and send. To select a different project or checkout, open **Workspace** while preparing this new chat and use **Create chat** in that form.

T3 creates the new conversation when you send its first message. Until then it is only an unsent draft. It inherits the selected chat's project, branch/worktree, interaction mode and permission mode. It gets a separate identity and never inherits the original chat's active run.

You can also use `/t3 new <message>` after choosing a project through a chat. Opening the game, pairing, viewing history, clicking an NPC and pinning a desk never start chats automatically.

## Meet the studio characters

Eighteen character slots represent up to eighteen chats. **Pause → T3Craft Settings → Characters** offers eighteen creator-made looks from the CC0 Minetest Skins Pack 1, with matching portraits and wide arm models. Legacy Original preferences use the new defaults. Changing a look preserves its desk, linked chat and custom name.

**General → Resident cast · Show all 18** applies the skin defaults and fixes staffing at eighteen. **Auto staffing** shows six to eighteen residents according to busy and pinned chats. Fixed staffing uses the chosen count. Unassigned NPCs do not create conversations or execute work. The modern campus supplies eighteen desk chairs and monitors plus meeting/archive seats. Upgrade the HQ to add the extra anchors to an older world.

Pinned chats and chats needing your response receive priority, followed by working/recent chats. Pins and custom character names belong to the current Minecraft world and dimension. Right-click a character and choose **Desk → Rename** to give it a name of up to 32 characters, then **Save**. **Reset name** restores the selected look's name when saved; **Cancel** discards edits. Names persist across restarts and chat reassignments, and can be changed while offline or without an attached chat. Its task/status still reflects the linked T3 conversation.

The modern campus puts all eighteen resident workstations together on the main daylight floor; the sidebar can browse all loaded chats. **Decisions** can surface waiting chats beyond those desks. A chat card represents a conversation rather than every internal dependency or subagent task inside it.

The eighteen desk assignments are shared across all paired machines. A saved desk assignment keeps a chat on the same character when the activity order changes. Right-click a character and choose **Desk → Reassign chat**, then search by chat title, project or machine and choose an existing conversation. This pins it to that character for the current world. **Desk → Pin desk / Unpin desk** controls roster priority; unpinning lets automatic staffing select the desk again. Assignment changes never create chats or alter a T3 workspace.

The character menu has four sections. **Chat** leads with the current next action and offers Reply, Steer run, Stop run, saved history and Your work. Reply uses the saved composer and queues behind an active run; Steer targets the actual active run. **Runs** offers queue management, delegated tasks/results, parent-chat navigation, activity and schedules. Its scrollable context shows fetched child progress and upcoming schedules bound to this chat; project schedules are available in the schedule screen. **Workspace** offers checkpoint review, worktrees/handoff, terminal, Git/PR controls, model options and chat settings. **Desk** offers reassignment, pins, rename and appearance. Machine, branch, worktree and model are shown in the scrollable context. Controls disable when the owning machine is offline or the desk has changed assignments while the menu was open.

| T3 state | Character behavior |
| --- | --- |
| Working | Walks to its main-floor desk or coworking seat; active receipts select reading, editing/typing, command watching, testing, review or thinking poses. Typing uses visible alternating hand taps, short mouse pauses and glances between the keyboard and monitor |
| Needs you | Up to two request owners approach inside the studio and face you; other waiting residents stay at their own desks. Nearby residents show a question thought bubble; distant ones keep the exclamation mark and request label |
| Ready checkpoint | Stays at its desk in a review pose with a Review ready label; the menu opens that chat's review |
| Done / idle | Returns to the lounge when no checkpoint needs review; residents take staggered snack, phone, stretch and relaxed developer-idle breaks |
| Newly completed run | Briefly smiles and nods with completion particles once; opening an already completed chat does not replay the reaction |
| Failed | Stays at its desk with a frustrated thought face, small head shake and a prompt to inspect the failure |
| Owning machine offline | Holds position with a dimmed offline label and nearby disconnected thought icon; typing stops and its lamp and monitor turn off |

A new completed assistant reply can appear in that character's speech bubble. Loaded history, repeated snapshots and desk reassignment do not replay old replies as speech. Each monitor reads its own linked chat's recent messages and activity. Background reads are bounded to one desk per maintenance tick, with working/waiting chats refreshed more frequently than settled chats.

Activity poses specialize only from active receipts belonging to the current run. Completed receipts and unknown tools keep a generic working presentation. A testing pose means a test command is running, not that it passed.

Occupied desk chairs show a small cosmetic keyboard and mouse with tapping keys. They follow the detected chair and desk without changing world blocks. Close thought bubbles show concentration or animated thinking dots above any skin, including helmets; real reply speech takes priority. Expressions describe the linked chat's state. Residents can give a brief greeting, stretch while seated and idle, shift their weight, point during review and lean toward a running command or test. Unassigned residents only use idle social gestures; paused or offline residents remain quiet.

## The T3 stations

**Pause → T3Craft Settings → General → Appearance** selects T3 Code, T3 Chat, Grove, Ocean, Ember or Iris in light/dark modes. Enter a six-digit **#RRGGBB** and choose **Save accent**, select a quick color, or choose **Default** to restore the palette accent. Invalid values cannot be saved. Appearance persists locally and applies to chat, menus, questions, HUD, monitor text, usage displays and physical task cards. Built-in colors match the pinned T3 Code palette snapshot; desktop theme selection and custom desktop themes are not synchronized.

**General → Display options** selects the atrium view: chat overview, a current status bar graph, recent chat activity, or native token usage and quota bars. **Right-click** on any monitor opens the picker for that specific connected panel, including while holding an item; right-clicking the atrium projector opens its picker; **Original display** restores its bound chat feed or usage view. The picker also opens the bound chat/board. Choices are local to the current world and dimension. The graph excludes offline-machine chats and labels that gap; it is a current count, not historical analytics. Selecting a display never sends a prompt.

Close nameplates remain visible at a capped size. Distant or crowded labels show the resident's name and status dot; nearby or targeted residents show activity and a separate project box. The atrium summary sits above the entrance sightline. Residents use nearby chairs within two blocks of their station, follow chair facing, leave a removed/obstructed chair, and stop to replan when a new block interrupts their next step. They keep body space, reserve separate destinations and yield or step around a crowded crossing. A sealed destination keeps them on safe ground until a route opens. Moving a chair farther away requires moving/rebuilding the station anchors. The front entrance carries a white/teal pixel-art T3 plaque.

The Minecraft main menu displays **T3 Craft** above the native game controls. Interaction menus share the selected appearance. Agent desks keep Chat, History and Review together; Workspace shows direct destinations in a searchable grid. Type a tool name or purpose into **Find a tool** instead of opening category menus. **Ctrl+F** focuses search; **Enter** opens a single matching result. With search focused, **Escape** clears the query first. Copy reply stays beside the composer on wide layouts. History stays in Workspace, and + New is the single entry for a new conversation. Workspace also includes activity and checkpoint review, with no duplicate copy or new-chat actions. Very small layouts page the results. Tool submenus return to the previous screen with **Back** or **Escape**, retaining the tool search and page. The chat panel closes back to the world with Escape. Chat, Your work, Workspace and Campus stay directly reachable from the primary screens. Workspace opens tools for the selected conversation; if none is selected, choose a chat first.

In the chat panel, **Ctrl+F** focuses search. Search by chat title, project or machine, then press **Enter** to open the first match. **Esc** clears a search before closing the panel. Searching a waiting conversation does not submit a question or approval.

Press **H** for the **HQ directory**. In your local creative HQ, select a destination to visit it; blocked destinations keep you in place. The directory includes the upper gallery, lower archive, stair hall and island waterfront, and also opens Chat, Decisions and the task board. Follow the clear south corridor on the work floor to the glass stair hall on the east: its southern return stairs lead upstairs, and its northern stairs lead to the lower archive. The island also has a garden studio behind the office and an east focus garden with shaded seating and water. Visits do not run on multiplayer servers or in other worlds. H can be rebound under Controls > T3Craft.

The **Usage room** beside the garden studio has a wall titled **My token usage & my spend**. It shows your paired machine's session/weekly allowance, remaining percentages and reset times, plus native token totals for the last 30 days. Cost is explicitly labeled **API-equivalent cost**, not your actual subscription bill. Missing sources and unpriced responses stay visible. The wall refreshes once a minute while in view and cycles through machines and additional limits without adding overlapping machines together. Rebuild the modern HQ after updating to add this room to an existing world.

| Studio feature | T3 variant |
| --- | --- |
| Task wall and task board | All loaded chats in Idle, Working, Needs you / failed and Done lanes; both the physical wall and screen page each lane independently |
| Character card | Exact chat summary, workspace, history, activity, decisions, stop and desk assignment |
| Desk monitor | Right-click chooses this panel's display; Open chat / board opens its linked conversation |
| Library shelves and catalog | Saved T3 chat history; search the full catalog and open the selected conversation |
| Decision podium | Actual questions and approvals across all loaded chats, including chats without a resident character |
| Checkpoint desk (original merge station) | Known ready T3 checkpoints on physical cards; click a card to inspect that chat's patch. Empty stations open the full review catalog |
| Test bench lamps | Connection health of paired T3 machines in order: teal online, red offline, unused slots dark; right-click opens Connections |
| Chat terminal | Native T3 chat panel and shared chat-status feed; Activity shows actual command outcomes |
| Atrium display and roof beacon | Choose overview, current status graph, recent chats or native usage; right-click the projector to change the atrium view. Open chat / board opens its related T3 screen |

The physical review cards advertise checkpoints already fetched for resident chats. The review catalog lets you inspect any loaded chat. A completed chat does not imply passing tests, successful CI or an approved merge. Decorations and building tools retain their original role.

The wall keeps readable full-size cards with up to three title lines and a project label. Lane counts include every chat across that lane's pages; recent chats appear first. Failed chats use red cards in **Needs you**, with their actual error shown. For faster browsing, right-click an empty wall area to open the board screen, or use the searchable library. Very long titles are shortened on the physical card; opening the card shows more of the title.

## Questions, approvals and all the waiting

A waiting chat displays the actual T3 request. Approvals expose **Yes** and **No**. Questions support suggested answers, free text when allowed, multiple choices, and multi-question requests. The answer is routed to the request's own chat and machine even if you select another chat before submitting it.

Right-click a character waiting on a question and choose **Chat → Answer request** to open the original studio-style paper dialog with their portrait, numbered answers and a custom-answer field. Click an answer or press **1–9**; for multiple choices, select the answers and press **Enter**. **Tab** focuses your own answer, **Shift+Enter** adds a line, and **Esc** leaves the question for later. Multi-question requests advance one question at a time and send together. The dialog confirms receipt by T3 and keeps your answer available for retry if sending fails.

Requests are checked again before sending. A resolved request or offline machine cannot receive a stale response. The mod never automatically accepts a request.

**Activity** displays backend timeline events, tool receipts, commands and outcomes. A failed command remains failed; the game does not fabricate a successful build or CI result.

The HUD and sound/toast notifications alert you when a chat finishes, fails, or needs your response. Press backtick soon after a notification to open the chat that notified you.

## Review and settings

**Review** loads the latest ready T3 checkpoint and its patch, including file changes, line numbers and hunk navigation. Feedback returns to that existing chat. A diff does not merge a branch or publish changes.

**Settings** can rename the chat, choose Chat/Plan interaction mode, and set Ask permission / Allow edits / Full access. These controls update the actual T3 thread. Permission semantics remain those enforced by T3 and its provider.

Open **Workspace** in the chat panel for the advanced workflows below. Every screen retains the selected chat's owning machine. An offline or removed pairing blocks writes. Forms provide a direct Connections action and retain their fields when returning. Requests already submitted continue in T3; leaving the screen does not cancel them.

## Messaging and attachments

Drafts persist when you change chats, close a screen or restart the game. **Expand** opens a multiline composer: Enter inserts a newline and Ctrl+Enter sends. A send clears its draft after T3 acknowledges it. If it fails, the draft remains available for an explicit retry using the same command identities. Interrupted sends are never replayed automatically.

**Queue / Steer** chooses how a reply reaches an active run. **Run queue** reads its queued messages; load one to edit it, move it first, cancel it, or steer the current run with it. **Resume queue** uses T3's native control.

**Attach** stages local paths, including supported images. Press F2 in the world, then choose **Latest screenshot** to attach that capture. Files upload to the owning T3 machine only when you send. **Copy reply** copies the latest loaded assistant reply. History searches the pages already loaded; load older pages to search farther back.

## Workspaces and models

**New chat & workspace** lists projects and usable models from that machine. Select projects and models by name, choose Project root, New worktree, or Existing worktree, and supply a first message. Only fields for that choice appear: root needs no branch/path fields, a new worktree needs a base ref and branch, and an existing worktree needs its absolute path. Changing the choice retains the values already entered. T3 prepares and binds the workspace before starting the conversation. Uncommitted edits are not copied.

**Worktrees & handoff** moves the current idle chat from its root checkout into a new worktree. Stop its active run first. If worktree creation succeeds but binding fails, retry binds the same created worktree. A chat already in a worktree must use a new conversation to switch workspaces.

**Model options** reads provider-specific select and boolean controls, such as reasoning effort, from the current model's catalog. **Inherit** restores its default. **Chat settings** also exposes Archive chat and Fork chat; **Archived chats** can read and restore saved conversations.

## Terminal, tasks and schedules

If this machine was paired with an earlier build, create a fresh T3 Settings → Connections link and replace its pairing to enable terminal and review permissions. Open **Terminal** directly from Chat, **Workspace → Terminal**, or **Ctrl+`** in Chat. It uses the selected chat's owning machine, checkout and provider. Enter sends the command; Tab and Ctrl+C are also available as buttons. Ctrl+C copies selected input text; with no selection it interrupts the shell. Unsent input is saved privately per machine/chat and survives navigation and Minecraft restart. It clears only after T3 acknowledges that same input. After a failed send, **Reconnect** reattaches without replaying the command; inspect shell output before retrying uncertain delivery. **Restart shell** appears when the shell exits or closes. Back, Escape and Ctrl+` detach the view and leave the shell alive; **End shell** explicitly stops it. Terminal dimensions follow GUI scale. This bounded text view supports ordinary shell commands and prompts; full-screen terminal applications still work best in T3 Code.

**Delegated tasks** lists the parent's children, status, progress and results. Choose a model number and task brief to delegate from an active parent run. **Open child chat** reads its backing conversation; **Cancel task** interrupts app-owned child work and disposes its completion delivery.

**Schedules** supports minute intervals and local HH:MM schedules, including custom weekday numbers (0 Sunday through 6 Saturday). Select current-chat delivery or a new chat each run. Load a schedule before editing; use the separate enable/disable, run-now and delete controls to manage it. Fixed times use the T3 machine's timezone. T3 persists and executes the schedule.

## Git and pull requests

**Git & pull requests** reads the actual current checkout, including a worktree selected through handoff. Commit, Push and Create PR are explicit actions. Paste a full HTTPS PR URL to read checks, update its body, submit a review, watch it or request a merge. PR actions link that PR to the T3 chat. Watches belong to T3 and wake the chat when checks, comments or conflicts change.

## Modern HQ

The modern HQ is an island campus with wooded ridges, rocky headlands, sheltered coves and sandy southern beaches. Follow the winding gravel trails from the gardens into the woodland and along the shore. Shallow coastal shelves descend into deeper ocean. The glass office has three occupied levels linked through a separate stair hall, eighteen desks, meeting rooms, a coffee lounge, terraces and gardens. New worlds generate ocean outside the island; existing HQ worlds gain the coastal terrain as chunks load. On Windows, close Minecraft and double-click `Update-T3Craft.cmd` to back up your installed world, update the mod and upgrade the HQ automatically on next launch. The script prints the verified backup directory and preserves shaders, options and pairing. You can also run `/agentcraft hq modern` in-game. The builder preserves changes recorded against the previous HQ plan; `force` resets those changes. `/agentcraft hq studio` selects the original building style.

## Reconnect, update and pack it up

Connections shows each paired machine's health and lets you retry, replace a pairing, or remove it. An offline machine's cached display can remain visible with an offline state; its write controls require reconnection. A healthy second machine remains usable.

Close the native Minecraft instance before updating, then rerun `Install-T3Craft.cmd`. The installer updates the mod JARs while preserving the instance's world, options and pairing. First-party downloads are checksum checked.

Removing a machine from Minecraft forgets its local credentials. To revoke access at the server too, remove its **Minecraft** or **T3Craft** connection in T3 Settings → Connections. Keep `config/t3craft.json` private; it is not part of the import ZIP or Git checkout.

See [Prism troubleshooting](PRISM.md#when-launch-day-gets-weird) for login, Java and shared-library lock problems.
