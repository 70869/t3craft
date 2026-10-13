# Install T3Craft with Prism Launcher

## Recommended: import the release ZIP on any platform

**[Download T3Craft for Prism](https://github.com/70869/t3craft/releases/latest)** — Windows, macOS and Linux.

1. Install [Prism Launcher](https://prismlauncher.org/download/) and sign into your Minecraft account in **Settings → Accounts**.
2. Download **T3Craft-Prism.zip**. In Prism choose **Add Instance → Import** and select the ZIP. Older releases call it **T3Craft-26.3-Native.zip**.
3. Launch **T3Craft 26.3 Native** with T3 Code running. Connections opens on the first unpaired world entry; paste a fresh link from **T3 Settings → Connections**. The guide then helps you choose an existing conversation.

No source checkout, Python, Gradle or Node is needed. The package contains Minecraft/Fabric component definitions, the two required mod JARs and a clean copy of the latest authored campus world; Prism downloads Minecraft itself. In **Prism Settings → Java**, enable **Autodetect Java version** and **Auto-download Mojang Java**. Some Linux packages omit Java downloads; select Java 25+ manually in that case. [Prism's Java guide](https://prismlauncher.org/wiki/getting-started/installing-java/) covers these options.

Releases produced by this checkout also include a version-labeled ZIP, `compatibility.json`, and `SHA256SUMS.txt`. They describe the exact Minecraft/Fabric target and hashes of the bundled files. Use a separate instance for a different Minecraft version; never downgrade an existing saved world to make a JAR load.

## Optional Windows installer

Releases produced by this checkout provide **T3Craft-Windows-Installer.zip**. Extract the ZIP and double-click `Install-T3Craft.cmd`. The installer contains the same prebuilt Prism package and latest authored world as the release. It verifies the bundled SHA-256 before creating the dedicated instance using Prism's Java setup. Minecraft and Java may still need downloads through Prism. It does not compile sources or issue a T3 pairing grant; pair privately in the game.

Use the installer from the newest release with Minecraft closed to update the same instance. The installed update helper explicitly selects the latest published release. Before changing the two managed mods, it verifies a backup of every save, installed mods, config and instance definition under `backups/t3craft/<instance>/<timestamp>/`. Pairing, settings, other mods and shaders stay in place. Running/locked worlds, a different Minecraft/Fabric target, and older T3Craft versions are refused. The helper is kept in the instance's config directory, so **Settings → Update mod** can save/close the game, back up, install and relaunch without a source checkout. A mod update does not queue a campus rebuild.

For a portable launcher, a new instance ID, or a local verified package:

```powershell
.\tools\install-release.ps1 -PrismData 'D:\Games\Prism' -Launch
.\tools\install-release.ps1 -InstanceId T3Craft-Try -NewInstance
.\tools\install-release.ps1 -PackagePath .\T3Craft-Prism.zip -ExpectedSha256 <published-sha256> -WhatIf
```

The installer downloads require a release containing `T3Craft-Prism.zip` and `SHA256SUMS.txt`; older releases remain importable through Prism. The source-checkout launcher below remains usable before the next release is published.

## Build and install from a source checkout (Windows)

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

## macOS and Linux launcher locations

Use the recommended import above. Prism stores instances in its configured instances folder; its default data folders are `~/Library/Application Support/PrismLauncher` on macOS and `~/.local/share/PrismLauncher` on Linux. Flatpak Prism uses `~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher`.

The import ZIP includes T3Craft and Fabric API. Prism can manage the Java runtime; if you select one manually, use Java 25 or newer. Keep T3 Code running, open Connections in Minecraft, and pair with a link from T3 Settings → Connections. The Windows-only `.cmd` installer is not used on macOS or Linux.

## Compatibility

| Component | This build |
| --- | --- |
| Minecraft Java | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25+ |
| T3 pairing | Paired environment client, protocol 2 verified live |
| Desktop OS | Windows, macOS, Linux (release build matrix; native game launch verified on Windows) |

Existing Minecraft 1.21.x Fabric instances and ATM10/NeoForge cannot load this JAR. Use the separate instance. The internal mod ID is `agentcraft`; install only one AgentCraft/T3Craft JAR in an instance.

## Importable ZIP

The cross-platform release packager creates **`artifacts/prism/T3Craft-Prism.zip`**, a version-labeled ZIP and the legacy **`T3Craft-26.3-Native.zip`** alias. The source Windows installer creates the legacy import ZIP.

In Prism use **Add Instance → Import**, select that ZIP, and finish the import. The ZIP contains the instance definition, T3Craft, Fabric API and the reviewed authored world. `world-template.json` records its saved date, layout revision and file hashes. Player files, entities, inventories, profiles and personal configuration are removed; authored buildings, sign text and terrain remain. It contains no credentials, chat history, account files or machine-specific Java path. Select/download Java 25 through Prism if automatic Java selection does not find it, then pair in the game. Existing-instance updates retain their own saves.

Prism references: [creating an instance](https://prismlauncher.org/wiki/getting-started/creating-an-instance/), [installing mods](https://prismlauncher.org/wiki/getting-started/download-mods/), and [Java setup](https://prismlauncher.org/wiki/getting-started/installing-java/).

## Update the mod without rebuilding the campus

Run `tools/update-hq.ps1 -ModOnly` with Minecraft closed. It verifies a complete HQ backup, backs up the old mod, and installs the current build while preserving pairing, settings and shaders. `-ModOnly -WhatIf` previews the update. The normal updater still queues the campus upgrade; use `-ModOnly` for interface and workflow updates.

`Update-T3Craft.cmd` and the source-backed in-game updater select `-ModOnly` for routine updates. To rebuild the campus explicitly, use `tools/update-hq.ps1 -Launch` without `-ModOnly`, or confirm **Upgrade HQ** in the game. On macOS/Linux, close the game, back up the instance through Prism, and replace only T3Craft and Fabric API under **Edit → Mods** using the new release ZIP; retain the existing save and settings directories.

## Manual mod installation

Create a **Minecraft 26.3** instance in Prism. In **Edit → Version**, install **Fabric Loader 0.19.5**. Add these files under **Edit → Mods**:

- `mod/build/libs/t3craft-1.0.1.jar`
- Fabric API `0.161.0+26.3`, also included in the generated import ZIP.

Select Java 25+, launch, press backtick, and pair via Connections. The world auto-load setting is on in the generated dedicated profile; manual installations follow the mod's default world behavior.

## Optional developer inventory

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

**Installer cannot find release checksums:** That release predates the prebuilt installer. Import its Prism ZIP directly, or use the source-checkout installer. Do not bypass checksum verification.

**Restore an update:** Close Minecraft. For a release-installer backup, restore its `saves`, `mods` and `config` directories under the existing game directory, plus `instance.cfg` and `mmc-pack.json` at the instance root. Source HQ backups keep `AgentCraft HQ` and `mods` directly under the printed backup path. Use the matching backup layout and keep its original Minecraft version.

On 2026-10-05 the native mod connected in a Minecraft development client to the live T3 environment. The dedicated Windows Prism instance launched and showed the HQ and residents. The release build matrix checks compilation on Windows, macOS and Linux; native game launches on macOS and Linux have not yet been verified.
