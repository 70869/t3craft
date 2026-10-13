# Authored release world

`world-template.zip` is the clean snapshot of the author's latest saved native **AgentCraft HQ**, not a fixture or a newly generated campus. `world-template.json` records the source save time, layout revision, snapshot checksum and every exported file hash. Package builds on Windows, macOS and Linux use this same checked-in snapshot and reject mismatched Minecraft targets, unexpected files and changed hashes.

Before preparing a release tag, save and close the author's native world and export it again. The export holds Minecraft's world lock, verifies that the source did not change, rewrites live chunks into fresh region files, and excludes player files, entities, inventories, item/book profiles, scoreboards, scheduled events, account configuration and pairing data. Authored blocks, sign text, station bindings, terrain, anchors and the previous building plan are retained. Review authored sign text before publishing.

```powershell
$save = Join-Path $env:APPDATA 'PrismLauncher/instances/T3Craft-26.3-Native/minecraft/saves/AgentCraft HQ'
.\mod\gradlew.bat -p mod worldTemplateExport "-PworldSource=$save"
python tools/package-prism.py --verify-world-source "$save"
```

Select the configured Java toolchain and isolated Gradle home as described in [developer checks](../tools/README.md). For a custom Prism profile, pass its actual closed save directory. Include both snapshot files with the release's source changes. `--verify-world-source` refuses packaging if the authored save has changed after capture. CI validates the committed snapshot without accessing the author's profile.

New Prism imports and new Windows installations receive this world. Updates to an existing instance replace only the managed mods and keep its saved worlds unchanged. Loading the template uses its saved anchors, so first launch does not rebuild the authored campus.
