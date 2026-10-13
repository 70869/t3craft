# T3Craft 1.0.1

T3 Code inside Minecraft, with a compact chat workspace and the latest authored campus included for new players.

## What changed

- T3 menus use a compact scale at 1080p and restore the player's world GUI scale when closed. Search and New lead the sidebar; machine/project/status filters sit behind Filter. Working and Settled have separate collapsible shelves, and the selected thread stays visible.
- Residents show colored work states, visible typing bursts and pauses, separate names and thread context, and completed lounge gestures for snacks, phones, stretches and relaxed idle time. Updated T3 receipts refresh resident review state promptly.
- All eighteen resident work assignments stay on the main floor. Older authored campuses reuse their existing coworking seats while preserving saved buildings and anchors.
- The standalone Windows installer includes the verified prebuilt Prism package. Fresh installations receive the authored world; updates back up and preserve existing saves, pairing, options, shaders and other mods.

## Install

Install [Prism Launcher](https://prismlauncher.org/download/) and sign into your Minecraft account. On Windows, extract **T3Craft-Windows-Installer.zip** and run **Install-T3Craft.cmd**. On Windows, macOS or Linux, import **T3Craft-Prism.zip** through Prism's **Add Instance → Import**. Launch with T3 Code running and pair through Connections in Minecraft.

Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3 and Java 25+. The package contains no T3 credentials, account files, private player data or chat history. SHA-256 checksums accompany the release.

## Included world

The cleaned snapshot comes from the author's native save at **2026-10-12 23:44:10 UTC**: modern layout revision **9**, **11,055 chunks**. It preserves the authored buildings, terrain, signs, stations and saved layout. Every fresh Prism import and standalone installation uses this same snapshot. [The manifest](../release/world-template.json) records its provenance and hashes; release preparation refuses a stale capture.

## Verification

The Windows Java 25 build, authored-world work-floor check, native menu/navigation/theme checks, sidebar click and scale checks, resident animation and crowd checks, paired protocols 1/2, twelve package regressions, eight Node tests and disposable installer recovery checks passed. Mutating T3 checks use synthetic environments. Native macOS/Linux gameplay, additional shader combinations and real remote writes remain outside this verification.

See [installation and recovery](PRISM.md), [controls](USER-GUIDE.md) and [integration evidence](T3-INTEGRATION.md).
