# Bloat audit

## Removed

The old `foreman/` backend (67 tracked files) was removed, including the Claude SDK execution path, simulator, agent scheduler, separate chat/memory store, notifications, repository orchestration and its obsolete tests/dependencies. The interim MCP adapter and fake sandbox/demo generator were removed too.

Old launch/QA clients that required Foreman were removed. The Windows launcher now delegates to the native installer. Ordinary game interactions route to T3 screens. No Node, npm dependencies or second orchestration process is required to play.

The unused stop/process runner scripts and duplicate upstream README were removed. Historical design and QA references are labeled so they cannot be mistaken for current setup instructions.

## Still present

| Remaining material | Why it remains | Next cleanup |
| --- | --- | --- |
| Internal `ForemanSnapshot` / protocol names | The original HQ renderer consumes them; T3Projection supplies real T3 state | Rename and narrow the rendering model |
| Dormant `ForemanLink` and old agent/console/decision/diff/library screens | Disabled runtime connection; old developer commands still refer to some classes | Ordinary interactions and standard screen aliases now use T3; remove the remaining legacy developer commands, then delete these classes |
| DevBridge and nested Java-WebSocket JAR | Opt-in game screenshot/inspection tools; disabled in installed profile | Separate into a developer artifact if a smaller release is wanted |
| `tools/scene.mjs` mock studio fixtures | Isolated rendering tests, never a live chat source | Keep only useful renderer tests |
| Original art, source art, screenshots and historical design/QA docs | Studio assets and provenance | Move historical reference material out of the distributable checkout |
| Ignored `.local`, `.gradle-home`, `.references`, `mod/build`, `node_modules`, `artifacts` | JDK, build caches, inspected references and test evidence | Delete selectively when reclaiming disk; rebuild/redownload needed afterward |

Only compiled game assets/classes and the small DevBridge dependency ship in the mod JAR. The local JDK, source art, reference checkouts, tool dependencies and documentation images do not ship. Build caches are disk usage, not extra agent execution.

The next valuable cleanup is replacing the old studio DTO names and removing dormant Java panels/link code. Rewriting the original studio renderer solely to remove those names is a larger change than removing the old backend.

## Feature scope

Pending native T3 controls are feature work, not bloat. They include archived chats, attachments, terminal interaction, scheduler/delegation management, provider options, worktree authoring, steering/queue editing and PR operations. See [coverage](T3-INTEGRATION.md#feature-coverage).
