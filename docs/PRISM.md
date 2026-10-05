# Install T3Craft with Prism Launcher

## Easiest setup on Windows

Install [Prism Launcher](https://prismlauncher.org/download/) and open T3 Code. Sign into your Microsoft Minecraft account in **Prism → Settings → Accounts**.

Double-click **`Install-T3Craft.cmd`** in this checkout. It:

1. Finds Prism and obtains checksum-verified Java 25 if the checkout has no local JDK.
2. Builds the native Fabric mod when needed.
3. Creates or updates the dedicated **T3Craft 26.3 Native** profile.
4. Pairs to the running local T3 app when its pairing CLI is available.
5. Asks Prism to launch the instance.

No Node process or external bridge is required. Keep T3 Code open while playing. Press backtick (`) for the panel; if needed, paste a fresh T3 Settings → Connections pairing link into the game's Connections screen.

For a portable/custom Prism data directory:

```powershell
.\tools\install.ps1 -PrismData 'D:\Games\PrismLauncher' -Launch
```

To install without launching, omit `-Launch`. To defer automatic pairing, add `-SkipPair`. To rebuild explicitly, add `-Build`.

Repeat the installer to update. Close the native game first. Worlds, options and pairing are preserved.

## Compatibility

| Component | This build |
| --- | --- |
| Minecraft Java | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25+ |
| T3 pairing | Paired environment client, protocol 2 verified live |

Existing Minecraft 1.21.x Fabric instances and ATM10/NeoForge cannot load this JAR. Use the separate instance. The internal mod ID is `agentcraft`; install only one AgentCraft/T3Craft JAR in an instance.

## Importable ZIP

The installer writes **`artifacts/prism/T3Craft-26.3-Native.zip`**.

In Prism use **Add Instance → Import**, select that ZIP, and finish the import. The ZIP contains the instance definition, T3Craft and Fabric API. It contains no credentials, chat history, account files or machine-specific Java path. Select/download Java 25 through Prism if automatic Java selection does not find it, then pair in the game.

Prism references: [creating an instance](https://prismlauncher.org/wiki/getting-started/creating-an-instance/), [installing mods](https://prismlauncher.org/wiki/getting-started/download-mods/), and [Java setup](https://prismlauncher.org/wiki/getting-started/installing-java/).

## Manual mod installation

Create a **Minecraft 26.3** instance in Prism. In **Edit → Version**, install **Fabric Loader 0.19.5**. Add these files under **Edit → Mods**:

- `mod/build/libs/t3craft-0.2.0.jar`
- Fabric API `0.161.0+26.3`, also included in the generated import ZIP.

Select Java 25+, launch, press backtick, and pair via Connections. The world auto-load setting is on in the generated dedicated profile; manual installations follow the mod's default world behavior.

## Detected Minecraft instances on this machine

The initial read-only inventory found ATM10 (1.21.1 NeoForge), RTX ON (1.21.8 Fabric), VR Starter/Vivecraft (1.21.10 Fabric), Vr+ (1.21.1 Fabric), and a vanilla 26.3 instance. Setup added the older **T3Craft-26.3** demo profile and the current **T3Craft-26.3-Native** profile. Existing packs were preserved.

Refresh the inventory with the optional developer detector:

```powershell
node tools/detect-minecraft.mjs --output artifacts/minecraft-instances.json
node tools/detect-minecraft.mjs --root 'D:\Games\PrismLauncher'
```

The detector does not read account credentials. Its output is ignored by Git.

## Troubleshooting

**Microsoft session expired:** Reauthenticate in Prism → Settings → Accounts, then launch again.

**Access denied writing Prism libraries:** Exit other Minecraft instances using the same Prism data directory. A closed window can leave a Java process alive; confirm it has exited before retrying. The native profile has a separate world/mod directory but Prism's library cache is shared. Do not remove shared libraries while a game holds them open.

**Java error or unsupported class version:** Select Java 25 or rerun the installer. This checkout's local JDK avoids the older system Java.

**No T3 chats:** Keep T3 Code running; check Connections, the selected machine, and the project filter. Create a fresh pairing link if access expired or was revoked.

**Pairing link expired:** Generate a new link in T3 Settings → Connections. Links are short lived and one use.

**Protocol incompatible:** Update the mod and T3 together. The game refuses unrecognized future protocols before consuming a pairing link.

**Automatic local pairing unavailable:** Use the game's Connections screen. The installer still prepares the profile.

On 2026-10-05 the native mod connected in a Minecraft development client to the live T3 environment. The dedicated native Prism instance is installed and paired, but its launch has not yet been verified because the older Java game still holds shared library files.

