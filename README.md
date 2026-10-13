# T3Craft

Your T3 Code workspace inside a modern Minecraft campus office. The native Fabric mod connects directly to paired T3 Code machines. T3 owns the chats, providers, workspaces and execution.

The primary destinations are Chat, Your work, Workspace and Campus. Workspace tools are searchable; conversation and NPC actions follow the current request, review, run or failure state. Advanced forms retain their fields across Connections, and terminal input remains until acknowledged.

## Install with Prism

**[Download T3Craft for Prism](https://github.com/70869/t3craft/releases/latest)** — Windows, macOS and Linux.

1. Install [Prism Launcher](https://prismlauncher.org/download/) and sign into your Minecraft account.
2. Download **T3Craft-Prism.zip** from the release. In Prism choose **Add Instance → Import** and select it. Older releases use the name **T3Craft-26.3-Native.zip**.
3. Launch the new instance with T3 Code running. On the first unpaired world entry, Connections opens automatically: paste a fresh link from **T3 Settings → Connections**.

Use Prism's Java autodetection and download settings, or select Java 25+. No source checkout, Gradle, Node or separate AI account is needed to play. [Installation and recovery](docs/PRISM.md) covers launcher packages without automatic Java downloads.

New release imports include a clean copy of the latest authored T3Craft world, preserving the campus buildings and terrain. Its [snapshot manifest](release/world-template.json) records the source save and checksums. Existing-instance updates keep their saved worlds.

For Modrinth App, import **T3Craft-1.0.1.mrpack** from the release. It includes the same authored world and exact checksummed mods. Select Java 25+ and open **AgentCraft HQ** from Singleplayer. Back up saves before launcher-managed pack upgrades; use the Prism installer for the supported backup-preserving update flow.

Releases built from this checkout also provide **T3Craft-Windows-Installer.zip**: extract it and double-click `Install-T3Craft.cmd` for a checksum-verified install from its bundled prebuilt package or a backed-up update. It keeps existing worlds, pairing and shaders. In a source checkout, the same launcher retains the local build/install workflow; `tools/install.ps1 -Build` builds explicitly.

- [How the mod works and controls](docs/USER-GUIDE.md)
- [Prism setup, ZIP import and troubleshooting](docs/PRISM.md)
- [Integration architecture and feature coverage](docs/T3-INTEGRATION.md)
- [Removed and remaining bloat](docs/BLOAT.md)
- [Developer checks](tools/README.md)

## History and new chats

The sidebar reads existing unarchived chats from your paired T3 environments. Selecting one loads its actual conversation; **Workspace → History → Load older** pages its saved timeline. No provider history folders, simulated chats or independent mod chat database are used.

Replies go to the selected T3 chat. **+ New** starts an unsent draft initialized from the selected conversation's project, checkout and modes. Send its first message to create the chat, or open **Workspace** while preparing it to choose a different project/model/checkout before **Create chat**. Merely opening Minecraft, pairing, choosing a chat or pinning a desk creates nothing.

Eighteen resident slots represent selected chats on the main floor, with saved desk assignments, visible state colors, animated typing, waiting approach and completion effects. Settled residents move to the lounge for staggered stretch, snack, phone and relaxed developer idle gestures. They reserve separate destinations, keep body space and yield or retreat around crowded crossings using the saved world's floor geometry. The task board, history library and Decisions desk include chats beyond those desks. Monitors show each resident's own T3 conversation, and checkpoint stations open T3 change review. [The guide](docs/USER-GUIDE.md#the-t3-stations) maps every interactive studio feature to its T3 variant.

Right-click a resident for **Chat**, **Runs**, **Workspace** and **Desk** controls: reply/steer/stop, questions and approvals, queues, delegated tasks, schedules, changes and checkout tools, plus searchable chat reassignment and pins. The menu shows the owning machine, branch/worktree and model. Current T3 receipts distinguish reading, editing, command execution, testing, review and thinking in the character's activity and posture.

**Your work** groups waiting requests, active runs, assigned desks, recent chats and failures, with the next action beside each conversation. The compact sidebar puts Search and New at the top, keeps scope controls behind Filter, and shows collapsible Working and Settled shelves from T3 state. **Ctrl+Tab** switches chats. T3 menus use a denser scale than the world HUD and restore the player's GUI preference when closed. A **Getting started** guide walks through pairing, selecting a chat, pinning a desk and composing a reply. Residents advertise requests, ready reviews and failures; at most two waiting residents approach while the rest wait at their desks. Commands have a distinct watching pose, and ready reviews keep their resident at its monitor.

## Compatibility

Minecraft **26.3**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.3**, Java **25+**. The installed profile is separate from existing 1.21.x or NeoForge packs.

The Fabric mod and Prism import target Windows, macOS, and Linux. GitHub Actions builds and validates the package on all three operating systems and checks protocols 1 and 2 against disposable fixtures. Windows also exercises installation, all-world backups and preservation of settings in synthetic profiles. The game has been launched on Windows; macOS and Linux still need native in-game launch verification.

The internal mod ID remains `agentcraft` for existing assets and worlds. Install one AgentCraft/T3Craft JAR per instance.

## Status and attribution

The 1.0.1 release candidate has been checked in an isolated Windows Minecraft 26.3 client with Java 25 and synthetic T3 pairings. Checks cover compact menus, navigation, all theme variants and eighteen community skins, paired protocols 1/2, package privacy, and installation/update recovery. The dated [integration checks](docs/T3-INTEGRATION.md) distinguish those results from earlier native-profile and live T3 checks. Native macOS/Linux launches and additional shader combinations still need game verification.

**T3Craft Settings → Characters** offers 18 creator-made looks from a CC0 skin pack. All resident slots use these skins; legacy Original preferences migrate to the new defaults. **Resident cast → Show all 18** keeps the full cast visible without creating chats. Newly generated campuses have eighteen main-floor desks. Older authored campuses reuse existing main-floor meeting and lounge seats for former upstairs worker assignments, preserving the saved blocks and anchors. Residents follow nearby moved or rotated chairs, leave obstructed seats and replan when blocks interrupt their route. A pixel-art T3 wordmark sits above the front entrance.

**T3Craft Settings → Appearance** offers T3 Code, T3 Chat, Grove, Ocean, Ember and Iris in light/dark modes, plus a saved custom accent. Chat, menus, questions, HUD, monitors, usage displays and task cards share the selected palette. **General → Display options** changes the atrium between chat overview, current status graph, recent activity and native tokens/limits. **Right-click** opens the picker for that individual monitor, including while holding an item. Display choices are saved per world. These local preferences use the pinned T3 Code built-in colors; desktop custom themes are not imported automatically.

Native controls now include durable drafts, steering and queues, attachments, model options, archived chats and forks, worktree creation/handoff, an interactive shell, delegated tasks, schedules, and Git/PR operations. Open **Workspace** from Chat, or use its direct **Terminal** button and **Ctrl+`** shortcut. [Coverage and validation limits](docs/T3-INTEGRATION.md) distinguish fixture verification from live T3 testing.

The HQ is a three-level glass campus on an island surrounded by ocean. A dedicated east stair hall connects the daylight work floor, upper collaboration gallery and lower archive with three-block-wide return stairs, broad landings and glass guards. Clear corridors keep circulation out of the meeting room and lounge. A wooded northwest ridge, rocky headlands, sheltered coves and southern beaches give the island varied terrain. Winding gravel trails connect the campus to its woodland and shore; underwater shelves descend into deeper ocean. The gardens include a reflecting pool, flower beds, a connected garden studio and a shaded focus garden. Press **H** for the HQ directory, including upstairs, downstairs and waterfront visits. T3 interaction menus use the selected appearance, grouped tools and **Ctrl+F** chat search. To upgrade an installed Windows world, close Minecraft and double-click **[Update-T3Craft.cmd](Update-T3Craft.cmd)**. It verifies a full world backup and installs the current build without rebuilding your campus; use the confirmed Upgrade HQ action for a campus rebuild. Surrounding chunks in existing HQ worlds gain the island terrain and ocean as they load. Shader packs, settings and T3 pairing remain in place. Recorded player changes are preserved. You can also run `/agentcraft hq modern` in-game; `/agentcraft hq studio` restores the original building style.

- Original studio: [blendi-remade/agentcraft](https://github.com/blendi-remade/agentcraft), MIT.
- Fork: [70869/t3craft](https://github.com/70869/t3craft).
- Official T3 Code: [pingdotgg/t3code](https://github.com/pingdotgg/t3code).
- Adapted paired client: [maxwellyoung/t3craft](https://github.com/maxwellyoung/t3craft), MIT.

Original artwork and licenses are retained. See [third-party notices](THIRD-PARTY-NOTICES.md).
