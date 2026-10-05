# Using T3Craft

T3Craft makes your existing T3 Code chats visible and actionable inside Minecraft. The HQ is a view of T3's state: chat messages, working agents, questions, approvals and completed changes come from your paired T3 machines.

## First launch

On Windows, open T3 Code, sign into Prism's Minecraft account, then double-click `Install-T3Craft.cmd` in the checkout. The first install needs internet and can take several minutes for Java, Minecraft, Fabric and Gradle downloads. Select **T3Craft 26.3 Native** in Prism.

If the game asks for a connection, create a pairing link in **T3 Settings → Connections**, copy the complete link, and paste it into **Connections → Add machine**. Give the machine a recognizable label. The link is exchanged for a paired-client credential; a used or expired link needs replacing.

The installer can pair automatically to a running local T3 desktop app using that app's supported pairing CLI. Automatic pairing does not create chats or send messages. Existing credentials are preserved on repeat installs.

Keep T3 Code running. The Minecraft Microsoft login and T3 pairing are separate.

## Everyday use

| Action | Control |
| --- | --- |
| Open chat panel | **Backtick** (`) or `/t3` |
| Open outstanding decisions | **J** or `/t3 decisions` |
| Inspect a character's linked T3 chat | Right-click the character or its monitor |
| Rename a character | Right-click the character → **Rename NPC** → enter a name → **Save** |
| Open the task board | Right-click an empty wall area, or `/t3 board` |
| Inspect a wall card | Right-click that card; **Chat**, **History**, **Activity**, **Review** and **Decisions** act on its chat |
| Turn a physical wall lane's page | Right-click the left half of its bottom **< page/pages >** strip for previous, or the right half for next |
| Open saved conversation history | **History** in the panel, the library station, or `/t3 library` |
| Review completed changes | **Review**, `/t3 review`, or the diff station |
| Change title or modes | **Settings** in the selected chat |
| Manage paired machines | **Connections** or `/t3 connections` |
| Pin a chat to an HQ desk | **Pin desk**; manage with **Pins** |
| Stop the selected chat's run | **Stop** or `/t3 stop` |

Rebind the panel and Decisions keys in Minecraft's Controls if another mod uses them. Escape closes a panel and preserves its unsent draft for this game session.

## Your existing chat history

Use the sidebar to choose a chat. Scroll its list and use the machine and project filters to find the right conversation. The conversation panel shows the recent T3 messages with basic Markdown formatting. **History** reads the saved timeline; **Load older** fetches the next older page, and the mouse wheel scrolls it.

The sidebar currently includes unarchived chats. Archived-chat browsing still needs a native screen. Chat drafts are held in memory, while the focused chat, machine credentials and desk pins are saved in Minecraft's `config/t3craft.json`. The mod does not import raw Codex or Claude history directories.

Only environments that you explicitly pair supply history. Removing a machine removes its chats from this mod. T3 remains the owner of the conversation history.

## Sending a reply

Select an existing chat, type in the composer, and press **Enter**. The message goes to that same T3 chat, using its project, workspace, model and modes. Enter sends and returns to the world; **Shift+Enter** sends and keeps the panel open.

If that chat already has an active run, the new message queues after it. The current native panel does not expose T3's separate steer-active action.

The model picker reads enabled, ready providers and their models from the selected machine. Picking a model takes effect on the next send and updates that thread's model. Some providers require a new chat to change model; those choices are labeled accordingly and require clicking **+ New** first.

## Starting a new chat deliberately

1. Select a conversation in the T3 project and workspace you want.
2. Click **+ New** beside the chat list.
3. Choose a model if needed, write the first message, and send.

T3 creates the new conversation when you send its first message. Until then it is only an unsent draft. It inherits the selected chat's project, branch/worktree, interaction mode and permission mode. It gets a separate identity and never inherits the original chat's active run.

You can also use `/t3 new <message>` after choosing a project through a chat. Opening the game, pairing, viewing history, clicking an NPC and pinning a desk never start chats automatically.

## The studio characters

The original six characters represent up to six chats. Pinned chats and chats needing your response receive priority, followed by working/recent chats. Pins and custom character names belong to the current Minecraft world and dimension. Right-click a character and choose **Rename NPC** to give it a name of up to 32 characters, then **Save**. **Reset name** restores its original cast name when saved; **Cancel** discards edits. Names persist across restarts and chat reassignments, and can be changed while offline or without an attached chat. Its task/status still reflects the linked T3 conversation.

There are six physical desks, but the sidebar can browse all loaded chats. **Decisions** can surface waiting chats beyond those desks. A chat card represents a conversation rather than every internal dependency or subagent task inside it.

The six desks are shared across all paired machines. A saved desk assignment keeps a chat on the same character when the activity order changes. Right-click a character and choose **Use selected chat** to attach the chat currently selected in your T3 panel to that character's desk. This pins it for the current world. **Pin desk / Unpin desk** on a task card controls roster priority.

| T3 state | Character behavior |
| --- | --- |
| Working | Walks to its own desk, sits and types |
| Needs you | Approaches you inside the studio, faces you, and shows an exclamation mark; waits by the podium when you are outside |
| Done / idle | Returns to the lounge |
| Newly completed run | Plays completion particles once; opening an already completed chat does not replay them |
| Failed | Shows the error state and effect |
| Owning machine offline | Holds position with a dimmed offline label; its lamp and monitor turn off |

A new completed assistant reply can appear in that character's speech bubble. Loaded history, repeated snapshots and desk reassignment do not replay old replies as speech. Each monitor reads its own linked chat's recent messages and activity. Background reads are bounded to one desk per maintenance tick, with working/waiting chats refreshed more frequently than settled chats.

## The T3 stations

| Studio feature | T3 variant |
| --- | --- |
| Task wall and task board | All loaded chats in Idle, Working, Needs you / failed and Done lanes; both the physical wall and screen page each lane independently |
| Character card | Exact chat summary, workspace, history, activity, decisions, stop and desk assignment |
| Desk monitor | That character's T3 messages and tool receipts; right-click opens its card |
| Library shelves and catalog | Saved T3 chat history; search the full catalog and open the selected conversation |
| Decision podium | Actual questions and approvals across all loaded chats, including chats without a resident character |
| Checkpoint desk (original merge station) | Known ready T3 checkpoints on physical cards; click a card to inspect that chat's patch. Empty stations open the full review catalog |
| Test bench lamps | Connection health of paired T3 machines in order: teal online, red offline, unused slots dark; right-click opens Connections |
| Chat terminal | Native T3 chat panel and shared chat-status feed; Activity shows actual command outcomes |
| Atrium display and roof beacon | Selected chat title and studio working/waiting totals; right-click a status lamp opens its related T3 screen |

The physical review cards advertise checkpoints already fetched for resident chats. The review catalog lets you inspect any loaded chat. A completed chat does not imply passing tests, successful CI or an approved merge. Decorations and building tools retain their original role.

The wall keeps readable full-size cards with up to three title lines and a project label. Lane counts include every chat across that lane's pages; recent chats appear first. Failed chats use red cards in **Needs you**, with their actual error shown. For faster browsing, right-click an empty wall area to open the board screen, or use the searchable library. Very long titles are shortened on the physical card; opening the card shows more of the title.

## Questions, approvals and activity

A waiting chat displays the actual T3 request. Approvals expose **Yes** and **No**. Questions support suggested answers, free text when allowed, multiple choices, and multi-question requests. The answer is routed to the request's own chat and machine even if you select another chat before submitting it.

Requests are checked again before sending. A resolved request or offline machine cannot receive a stale response. The mod never automatically accepts a request.

**Activity** displays backend timeline events, tool receipts, commands and outcomes. A failed command remains failed; the game does not fabricate a successful build or CI result.

The HUD and sound/toast notifications alert you when a chat finishes, fails, or needs your response. Press backtick soon after a notification to open the chat that notified you.

## Review and settings

**Review** loads the latest ready T3 checkpoint and its patch, including file changes, line numbers and hunk navigation. Feedback returns to that existing chat. A diff does not merge a branch or publish changes.

**Settings** can rename the chat, choose Chat/Plan interaction mode, and set Ask permission / Allow edits / Full access. These controls update the actual T3 thread. Permission semantics remain those enforced by T3 and its provider.

For terminal interaction, attachments, scheduler management, PR operations, archive/fork management and new-worktree authoring, use T3 Code for now. [The coverage table](T3-INTEGRATION.md) tracks those native gaps.

## Reconnect, update and remove

Connections shows each paired machine's health and lets you retry, replace a pairing, or remove it. An offline machine's cached display can remain visible with an offline state; its write controls require reconnection. A healthy second machine remains usable.

Close the native Minecraft instance before updating, then rerun `Install-T3Craft.cmd`. The installer updates the mod JARs while preserving the instance's world, options and pairing. First-party downloads are checksum checked.

Removing a machine from Minecraft forgets its local credentials. To revoke access at the server too, remove its **Minecraft** or **T3Craft** connection in T3 Settings → Connections. Keep `config/t3craft.json` private; it is not part of the import ZIP or Git checkout.

See [Prism troubleshooting](PRISM.md#troubleshooting) for login, Java and shared-library lock problems.
