# T3Craft tools

Playing needs the native Fabric mod and T3 Code. Node/Python below are optional developer dependencies.

## Install and package

```powershell
.\tools\install.ps1 -Launch
.\tools\install.ps1 -Build
.\tools\prism.ps1 -Install -Update
node tools/detect-minecraft.mjs --output artifacts/minecraft-instances.json
```

`Install-T3Craft.cmd` runs the installer from Explorer. `tools/launch.ps1` is a compatibility alias. The old Foreman/simulator launch modes have been removed.

`pair-t3.ps1` uses the installed T3 desktop pairing CLI, or accepts `-PairingLink`. It writes a private instance configuration, preserves existing config fields and prints no credentials. Prefer the game's Connections screen for manual pairing.

## Build and checks

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
.\mod\gradlew.bat -p mod connectionCheck featureCheck officeCheck studioCheck writeCheck
```

The fixture owns ports 25680/25681, accepts only synthetic tokens, and never contacts T3. Checks that mutate it are ordered to prevent interference. Run again with the fixture's `--protocol2` flag omitted to check compatibility paths; `writeCheck` targets protocol 2.

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

## Development game inspection

`mod/DEV.md` documents Fabric development-client and opt-in DevBridge controls. Set `T3CRAFT_CONFIG` to a paired development config; default Minecraft config remains `config/t3craft.json`. Keep private configs under ignored paths.

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

Render the HQ wall, then run `node tools/wall-check.mjs 27879` to traverse its physical pages through real use-key clicks, verify every chat remains reachable, check boundary clicks, and open a visible card. It only reads T3 state and navigates Minecraft UI. Evidence is written to `artifacts/qa/t3-wall.json`. `dev.taskwall {aimPage:"todo", previousPage:true}` returns a physical page-control aim point for QA.

The old stop/process-run helpers have been removed. Quit an installed game normally through Minecraft/Prism.

For NPC regression QA, launch the fixture with `--protocol2 --config artifacts/run/t3-studio-fixture.json`, launch the development game with that fixture config, and pin `A-done-0` to Marlow's desk. Then run `node tools/studio-check.mjs 27879`. The check verifies desk typing, waiting movement and exact request ownership, completion/error effects, new-reply speech, per-machine offline lights, recovery, navigation and station screens. It refuses a nonfixture game and only changes the two loopback fixtures. Evidence is written to ignored `artifacts/qa/t3-studio.json` and `artifacts/shots/`.
