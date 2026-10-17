# T3Craft tools: backstage, with the lights on

Playing needs the native Fabric mod and T3 Code. The scripts below are backstage equipment for building, packaging and checking the show; Node and Python are optional developer dependencies.

## Package the release

After creating a verified Prism release with `python tools/package-prism.py`, run `python tools/package-modrinth.py artifacts/prism/T3Craft-Prism.zip`. This creates the Modrinth `.mrpack`, resolves Fabric API by its exact SHA-512 on Modrinth, checks its Minecraft target, copies the validated authored world byte for byte, and adds the pack to `SHA256SUMS.txt`. T3Craft downloads from the matching GitHub release tag; publish that release before submitting the pack. The pack contains no installed-profile settings or pairing data.

```powershell
.\tools\install.ps1 -Launch
.\tools\install.ps1 -Build
.\tools\prism.ps1 -Install -Update
node tools/detect-minecraft.mjs --output artifacts/minecraft-instances.json
```

`Install-T3Craft.cmd` runs the installer from Explorer. `tools/launch.ps1` is a compatibility alias. The old Foreman/simulator launch modes have been removed.

Player installation defaults to the [portable release ZIP](../docs/PRISM.md). A released standalone Windows installer carries the prebuilt Prism package and runs `tools/install-release.ps1`, validating its bundled SHA-256, exact components and JAR metadata. Passing `-Release latest` or a release tag selects the published feed for later updates. The same script accepts `-PackagePath`, `-ExpectedSha256`, `-NewInstance` and `-WhatIf` for an offline preview or disposable-profile verification. Source-checkout launchers keep `tools/install.ps1` and local build behavior.

For routine source-checkout mod updates, close Minecraft and double-click `Update-T3Craft.cmd`; it uses `-ModOnly` and preserves the campus layout.

For an explicit campus upgrade, `tools/update-hq.ps1 -Launch` backs up every world file (with SHA-256 verification) and the old T3Craft JAR under Prism's `backups/t3craft/<instance>/<timestamp>/`, installs the build, and writes a one-time `t3craft-upgrade-modern` request into `saves/AgentCraft HQ`. Minecraft rebuilds the campus on its next start and consumes the request after saving. It requires a recorded studio/modern plan, keeps recorded player edits, and leaves shaders, options and pairing untouched. Use `-PrismData <path> -InstanceId <id>` for a custom Prism profile; `-WhatIf` previews the operation. A running/locked world is refused before any changes. To restore, close Minecraft and replace that HQ folder and T3Craft JAR with their copies from the printed backup directory. After this updater has run once, the pause menu's **T3Craft Settings → Update mod** action can save and close Minecraft, wait for the world lock to clear, run the `-ModOnly` backup/install flow, and relaunch Prism. The HQ directory also has a confirmed in-game modern-campus rebuild action that retains player-edited blocks.

`pair-t3.ps1` uses the installed T3 desktop pairing CLI, or accepts `-PairingLink`. It writes a private instance configuration, preserves existing config fields and prints no credentials. Prefer the game's Connections screen for manual pairing.

## Build it; check the wiring

Use a Java 25 JDK. This checkout's optional local JDK can be selected without changing system Java:

```powershell
$env:JAVA_HOME = (Get-ChildItem .local/java -Directory | Select-Object -First 1).FullName
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-home'
$env:JAVA_OPTS = '-Djava.net.preferIPv4Stack=true'
.\mod\gradlew.bat -p mod build
```

Start the synthetic fixture in another terminal:

```powershell
python tools/t3-fixture.py --port 25680 --protocol2
```

Then:

```powershell
.\mod\gradlew.bat -p mod connectionCheck featureCheck officeCheck studioCheck writeCheck workflowCheck
```

The fixture owns ports 25680/25681, accepts only synthetic tokens, and never contacts T3. Checks that mutate it are ordered to prevent interference. `workflowCheck` injects rejected commands and lost acknowledgments, verifies durable retries and uploads, and checks terminal bounds. `build` includes the pure office/studio checks and `hqLayoutCheck`. The latter assembles the actual modern block plan and checks office floors, corridor clearance, stair widths and headroom, guarded wells, bidirectional routes between floors using block collision shapes, coastal ridge heights, the sheltered cove, climbable trail slopes, the beach shelf and deep ocean. Run `python tools/check-workflows.py` to start disposable fixtures and check both protocols automatically, as CI does; explicit writes target protocol 2.

The optional `nativeProbe` task performs read-only checks against an explicitly paired config:

```powershell
.\mod\gradlew.bat -p mod nativeProbe '-Pconfig=../.agentcraft-home/native-t3/t3craft.json'
```

It prints counts and availability, never contents or credentials. It creates no chats, sends no prompts and answers no requests.

Optional screenshot-tool tests:

```powershell
npm --prefix tools ci
npm --prefix tools test
```

## Inspect the live set

`mod/DEV.md` documents Fabric development-client and opt-in DevBridge controls. Set `T3CRAFT_CONFIG` to a paired development config; default Minecraft config remains `config/t3craft.json`. Keep private configs under ignored paths.

Gradle `runClient` resolves the existing native Prism instance on Windows/macOS/Linux, including custom `InstanceDir`, Flatpak and `.minecraft` directories. Use `-Pt3craftPrismData=<path>` / `T3CRAFT_PRISM_DATA` for a portable launcher, or `-Pt3craftGameDir=<path>` / `T3CRAFT_GAME_DIR` for an explicit game directory. `gameDirectory` prints the selection; `build` only compiles/packages. Fixture QA must explicitly choose an isolated game directory; a separate pairing config alone does not isolate saves.

```powershell
node tools/devcli.mjs raw '{"type":"dev.t3"}' --port 27879
node tools/devcli.mjs screen t3 --port 27879
node tools/devcli.mjs screen t3-history --port 27879
node tools/devcli.mjs screen t3-settings --port 27879
node tools/devcli.mjs screen t3-board --port 27879
node tools/devcli.mjs screen t3-library --port 27879
node tools/devcli.mjs screen t3-reviews --port 27879
node tools/devcli.mjs shot t3-native-chat --hud --port 27879
node tools/devcli.mjs quit --port 27879
```

`dev.t3` returns connection and chat counts without private contents. DevBridge is disabled by default in the installed Prism profile.

Advanced screen aliases are `t3-tools`, `t3-archive`, `t3-options`, `t3-queue`, `t3-workspace`, `t3-worktrees`, `t3-terminal`, `t3-tasks`, `t3-schedules`, and `t3-git`. For fixture-backed game QA, launch with an explicit isolated directory such as `-Pt3craftGameDir=../.local/workflow-game-qa` and `T3CRAFT_CONFIG=artifacts/qa/t3-fixture.json` generated by the fixture and run `node tools/t3-workflow-qa.mjs 27879`. This drives the real forms, including mutations, only with the synthetic fixture credential. Start on `A-done-0`; the script creates an isolated new chat at its end. `dev.t3.form` is unavailable to real paired credentials.

`node tools/t3-recovery-qa.mjs <devPort> <fixturePort>` checks primary Chat/Your work/Workspace/Campus navigation at scales 2/3/4, named workspace choices, form fields across Connections, and terminal delivery failure, reconnect, and acknowledged retry. Use disposable fixtures only. `t3-workflow-qa.mjs` also accepts a fixture port and focuses the exact fixture chat before writes. Run checks sharing a game/fixture sequentially.

`node tools/t3-navigation-qa.mjs <devPort> [fixturePort]` checks the twelve distinct workspace tools at GUI scales 2/3/4, incremental search, retained results after Back, Ctrl+F, Enter and Escape, direct Chat terminal access and Ctrl+grave, History return, retained + New drafts, **+ New → Workspace** with mode-specific fields, hidden branch retention, and unsaved title retention when changing chat mode. Use a synthetic pairing; `dev.t3.menu` rejects real credentials. It captures `navigation-tools-2/3/4` and `navigation-workspace-2/3/4` in the configured shots directory. To inspect branding, start the isolated game with `AGENTCRAFT_AUTOWORLD=0` and capture the title screen.

`node tools/t3-terminal-qa.mjs <devPort> <fixturePort>` checks actual Minecraft terminal controls: saved input across navigation, streamed output, exact owner/thread, failed delivery and explicit retry, Ctrl+C, exit/restart/close, focus recovery, resizing and widget bounds at GUI scales 2/3/4. Use a disposable protocol-2 fixture; the menu guard rejects real credentials before mutation. Evidence is `artifacts/release-readiness/terminal.json`. Terminal write/resize/close fixtures return `null` successes like the native Effect RPC contract.

On Windows, `.\tools\check-terminal-live.ps1` additionally starts the installed T3 server CLI with a fresh private home and empty checkout under `.local/terminal-live-qa/`, on an available loopback port in 29316–29399. It runs `terminalLiveCheck` through the actual Java pairing/socket client and Windows PTY: streamed shell output and working directory, resize, interruption of a sleeping command, detach/reconnect history, restart and close. It preserves T3's startup thread state and dispatches no chat or provider run. The wrapper stops only its own server and removes its one-time grant in `finally`; it leaves the user's running T3 app untouched. This check requires an installed T3 desktop package with the server CLI and Java 25, and is an explicit opt-in rather than part of fixture CI. Older paired credentials need replacement to gain terminal/review permissions.

`t3-work` and `t3-guide` open the work inbox and setup guide. `node tools/t3-flow-qa.mjs <devPort> <fixturePort>` checks their actual widgets at GUI scales 2/3/4, search typed across ticks, retained chat navigation, Ctrl+Tab switching, exact-request and checkpoint actions, command-watching/review poses, the two-resident approach limit, and recovery actions for one offline machine. It refuses real pairings before changing synthetic state, assigns an existing fixture chat to Marlow locally, and creates no T3 conversations. Run it sequentially with other game checks using the same fixture. Primary navigation/recovery follow-up was also checked in a freshly generated HQ and the native installed Prism profile with Complementary Reimagined shaders; installed checks used synthetic pairing and restored original launcher/options files afterward. Evidence is `artifacts/qa/t3-flow.json`; screenshot paths use the game's configured shots directory. This change was checked with an isolated Windows game on DevBridge 27889 and fixtures 25686/25687.

Render the HQ wall, then run `node tools/wall-check.mjs 27879` to traverse its physical pages through real use-key clicks, verify every chat remains reachable, check boundary clicks, and open a visible card. It only reads T3 state and navigates Minecraft UI. Evidence is written to `artifacts/qa/t3-wall.json`. `dev.taskwall {aimPage:"todo", previousPage:true}` returns a physical page-control aim point for QA.

`node tools/t3-polish-qa.mjs 27879` verifies eighteen visible NPCs, community textures and wide models, switching between community looks, all twelve palette variants, saved accent across screens, and settings/menu bounds at GUI scales 2/3/4. Use the isolated protocol-2 fixture only. Evidence is written to `artifacts/qa/t3-polish.json`. Refresh the pinned upstream palette snapshot with `python tools/import-t3-themes.py`, then run the art pipeline and sync. `officeCheck` also verifies look defaults, model selection, theme colors/contrast and preference persistence.

The old stop/process-run helpers have been removed. Quit an installed game normally through Minecraft/Prism.

`node tools/t3-sidebar-qa.mjs <devPort> <fixturePort>` checks menu/world scale transitions, exact native Working/Settled row clicks, collapse/expand, visible focus, retained Back scope and no implicit writes. It uses the synthetic fixture's `/_qa/shelves` scenario; the guarded menu hook accepts `clickAt` only with fixture credentials. `t3-animation-qa.mjs` also checks actual rendered snack, phone and developer idle motion after the synthetic `/_qa/leisure` completion scenario.

## Release checks and compatibility

Release packages use the checked-in clean authored world under [`release/`](../release/README.md). Before preparing a release tag, save and close the latest native world, run `worldTemplateExport -PworldSource=<absolute-save-directory>`, and package with `--verify-world-source <save-directory>`. This verifies the current source against the captured template and refuses a stale export. Include the ZIP and manifest in the release's source changes. The exporter holds Minecraft's world lock, keeps authored blocks/signs/terrain and saved anchors, removes personal data, and leaves the source unchanged. Fresh installs receive the template; updates retain existing worlds. CI packages the same committed template on all platforms.

```powershell
python tools/package-prism.py
python tools/package-prism.py --validate-only artifacts/prism/T3Craft-Prism.zip
python -m unittest discover -s tools -p test_release_package.py
python tools/check-installation.py --package artifacts/prism/T3Craft-Prism.zip
python tools/check-source-update.py --package artifacts/prism/T3Craft-Prism.zip
```

`check-installation.py` runs on Windows with Windows PowerShell 5.1 and only synthetic disposable profiles. It verifies the canonical `70869/t3craft` release feed with locally substituted downloads, rejection of an unexpected asset origin, a new install, `-WhatIf`, custom paths, `.minecraft`, locked/incompatible-instance refusal, all-world backup hashes and retention of pairing/options/shaders/unrelated mods. Package validation runs on all three CI platforms and rejects unexpected or duplicate entries, private configuration and unapproved save files, local Java paths, corrupt JARs and mismatched dependency/version claims. `--api-jar <path>` lets the packager reuse a downloaded Fabric API JAR; bundled metadata and hashes remain checked.

`check-source-update.py` exercises `update-hq.ps1 → install.ps1 → prism.ps1` in a fresh Windows PowerShell 5.1 process with a disposable `.minecraft` profile and custom Prism directory. It uses the local Java 25 JDK from the source installer, runs the actual build, substitutes only the Fabric API download with the package's hash-checked JAR, and verifies ZIP creation, world/mod backups, installed hashes and preservation of worlds, pairing, options and shaders. The ZIP packager explicitly loads both compression assemblies because Windows PowerShell 5.1 does not resolve `ZipArchiveMode` from the FileSystem assembly alone.

For first-launch game evidence, launch an isolated empty game directory/config with DevBridge enabled, start protocol-2 fixtures at 25688/25689, and run `node tools/t3-installation-qa.mjs 27909 25688`. It verifies automatic unpaired Connections, dismissal/reopening, synthetic pairing, the follow-up guide at GUI scales 2/3/4, and zero dispatched T3 commands. It writes metadata/screenshots under `artifacts/qa/installation/`.

`node tools/t3-residents-qa.mjs 27879` verifies the eighteen distinct gaming residents, nearby chair relocation/rotation, obstruction recovery, close nameplates and saved display choices at GUI scales 2/3/4. Run against the isolated fixture in a modern HQ; it changes synthetic furniture only. Evidence goes to `artifacts/qa/t3-residents.json`.

`node tools/t3-animation-qa.mjs <devPort> <fixturePort>` checks typing bursts and pauses, mouse use, rendered hand motion, activity postures and focused/thinking/completion/failure/request/offline expressions without dispatching T3 work. Use an explicitly isolated game directory and synthetic protocol-2 fixture; the menu hook rejects real pairings before fixture mutation. It locally assigns an existing fixture chat to Marlow and restores fixture state at the end. Motion evidence is `artifacts/animations/qa.json`; screenshots use the configured shots directory and require visual inspection. `studioCheck` also exercises bounded asynchronous hand motion, gesture timing, expression priorities, live-only completion and two-/four-way movement clearance and arrival on the JVM.

For movement changes, extract `release/world-template.zip` into a new isolated game's `saves/AgentCraft HQ/` and launch it with a synthetic fixture configuration. Use the author's captured campus rather than generating a replacement. Run `node tools/t3-crowd-qa.mjs <devPort> <fixturePort>` to check 18 residents, head-on and four-way crossings, stationary bypass, shared destinations, coincident-position recovery and return to assigned desks. The guarded `dev.t3.residentWalk` preview only changes temporary client positions/routes, expires after 1200 ticks and supports `{clear:true}`; no T3 commands, saved assignments or world blocks are changed. `node tools/t3-gestures-qa.mjs <devPort> <fixturePort>` checks rendered idle seated stretches, greetings to an actual creative player, preserved seats and greeting expiry. It temporarily empties one synthetic machine so the fixed cast has idle residents, then restores that fixture. Run these mutating checks sequentially. Evidence is `artifacts/npc-behavior/crowd-qa.json` and `gestures-qa.json`; inspect screenshots from the configured shots directory.

For renamed campus-world startup, prepare a clean copy of the release world with Minecraft's title changed to `T3 HQ` using `-PfixtureGame=../.local/npc-world-identity/game2 prepareNpcWorldFixture`. Launch that isolated game with `AGENTCRAFT_DEV=1`, `T3CRAFT_CONFIG=artifacts/qa/world-identity/t3-fixture.json`, and `-Pt3craftGameDir=<fixture-game>`, after starting `python tools/t3-fixture.py --port 25694 --protocol2 --config artifacts/qa/world-identity/t3-fixture.json`. Run `node tools/t3-world-identity-qa.mjs <devPort>`; it confirms 139 modern anchors load and all synthetic residents get campus targets instead of the fallback spawn row. The game check creates no real T3 commands; screenshots/evidence are local QA files.

`node tools/t3-visual-qa.mjs 27899 25686 visual-final/` captures the ten current modern-campus views in `tools/scenes/qa.json`, using a dedicated development game paired only to the two protocol-2 fixtures at 25686/25687. The runner seeds three residents' activity in their exact owning fixtures and verifies that captures execute no T3 commands. It also checks checkpoint file selection, deleted-file review, change navigation and review/library widget bounds at GUI scales 2/3/4. Screenshots use the game's configured shots directory; the manifest and screen evidence go to `artifacts/qa/visual-pass/visual-final-manifest.json`. Follow [the visual review guide](../docs/visual-bar.md) to inspect the images; capture success alone does not establish appearance. `dev.t3.review {selectFile?:path}` permits readiness inspection and actual file-row clicks only for a synthetic owning environment. The default DevBridge remains disabled in installed profiles.

For NPC regression QA, launch the fixture with `--protocol2 --config artifacts/run/t3-studio-fixture.json`, launch the development game with that fixture config, and pin `A-done-0` to Marlow's desk. Then run `node tools/studio-check.mjs 27879`. The check verifies desk typing, waiting movement and exact request ownership, completion/error effects, new-reply speech, per-machine offline lights, recovery, navigation and station screens. It refuses a nonfixture game and only changes the two loopback fixtures. Evidence is written to ignored `artifacts/qa/t3-studio.json` and `artifacts/shots/`.

`node tools/t3-npc-qa.mjs 27879 25680` checks the grouped NPC controls at GUI scales 2/3/4, exact-machine approvals, queued replies and steering, workflow destinations, schedule context, activity postures, reassignment and unpinning. Use an isolated protocol-2 fixture game with `A-waiting` assigned to Marlow; the optional second argument selects its fixture port. It refuses real pairings, creates no chats, and records synthetic evidence in `artifacts/qa/t3-npc.json`. The question check also accepts a fixture port as its second argument and opens requests through Chat → Answer.

For the question dialog, use an isolated fixture game with `A-waiting` assigned to Marlow, then run `node tools/t3-question-qa.mjs 27879`. It drives real screen keys and mouse handlers for choice answers, multi-question requests, free text, failed-send retry, compact scrolling and withdrawn requests. The `dev.t3.question` inspection/click/scroll hook refuses real paired machines. Screenshots and protocol evidence go to `artifacts/shots/` and `artifacts/qa/t3-questions.json`.

`node tools/t3-menu-qa.mjs 27879` checks native menu clicks, Ctrl+F search, typing isolation while a question waits, widget bounds and overlaps at GUI scales 2/3/4, and HQ directory visits. Start with the default isolated fixture roster, rebuild the modern HQ, and keep the development player in creative mode for visits. `dev.t3.menu` refuses nonfixture pairings; evidence is saved to `artifacts/qa/t3-menus.json`.
