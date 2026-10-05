# T3Craft

Your T3 Code conversations inside AgentCraft's Minecraft studio. The native Fabric mod connects directly to paired T3 Code machines. T3 owns the chats, providers, workspaces and execution.

## Install on Windows with Prism

1. Open T3 Code and sign into your Minecraft account in Prism.
2. Double-click **[Install-T3Craft.cmd](Install-T3Craft.cmd)**.
3. Launch **T3Craft 26.3 Native**. Press **backtick** (`) for your chats.

The installer downloads and verifies Java 25 if needed, builds the mod, installs a dedicated Prism instance, and pairs to the running local T3 app when supported. It requires internet on first install. No Node, Claude SDK, Foreman process or separate AI account is needed to play.

If automatic pairing is unavailable, use **Connections** in the game and paste a fresh link from **T3 Settings → Connections**. Keep T3 Code running while playing.

- [How the mod works and controls](docs/USER-GUIDE.md)
- [Prism setup, ZIP import and troubleshooting](docs/PRISM.md)
- [Integration architecture and feature coverage](docs/T3-INTEGRATION.md)
- [Removed and remaining bloat](docs/BLOAT.md)
- [Developer checks](tools/README.md)

## History and new chats

The sidebar reads existing unarchived chats from your paired T3 environments. Selecting one loads its actual conversation; **History → Load older** pages its saved timeline. No provider history folders, simulated chats or independent mod chat database are used.

Replies go to the selected T3 chat. **+ New** explicitly creates a chat in the selected conversation's T3 project and workspace, using its modes and chosen model. Merely opening Minecraft, pairing, choosing a chat or pinning a desk creates nothing.

Six original HQ characters represent selected chats, with saved desk assignments, own-desk typing, waiting approach and completion effects. The task board, history library and Decisions desk include chats beyond those six desks. Monitors show each resident's own T3 conversation, and checkpoint stations open T3 change review. [The guide](docs/USER-GUIDE.md#the-t3-stations) maps every interactive studio feature to its T3 variant.

## Compatibility

Minecraft **26.3**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.3**, Java **25+**. The installed profile is separate from existing 1.21.x or NeoForge packs.

The internal mod ID remains `agentcraft` for existing assets and worlds. Install one AgentCraft/T3Craft JAR per instance.

## Status and attribution

The paired protocol-2 client has been verified against a running T3 Code Nightly and in a Minecraft development client. Fixture checks cover explicit chat writes, decisions, routing, reconnects and checkpoint review. NPC movement, speech and station screens passed isolated in-game checks. The native Prism instance now launches successfully: its log confirms the HQ and six residents, and the user's screenshot confirms the connected task wall. Native startup also recorded NPC pathfinding fallbacks, which need a separate navigation check.

This is a working chat and review integration; the [feature coverage table](docs/T3-INTEGRATION.md) identifies advanced T3 features that still need native Minecraft controls.

- Original studio: [blendi-remade/agentcraft](https://github.com/blendi-remade/agentcraft), MIT.
- Fork: [70869/agentcraft](https://github.com/70869/agentcraft).
- Official T3 Code: [pingdotgg/t3code](https://github.com/pingdotgg/t3code).
- Adapted paired client: [maxwellyoung/t3craft](https://github.com/maxwellyoung/t3craft), MIT.

Original artwork and licenses are retained. See [third-party notices](THIRD-PARTY-NOTICES.md).
