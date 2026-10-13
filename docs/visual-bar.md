# T3Craft visual review

T3Craft should read as a finished Minecraft workspace: a calm modern campus, recognizable residents,
and useful T3 state that can be understood in a few seconds. Inspect the actual game; a compile or
successful screenshot capture does not establish visual quality.

## Campus and lighting

Use pale wood, warm stone, dark furniture and structural frames, glass, and planted grounds. Give
each room a focal point and clear circulation. Keep the entrance sightline open, including around
the atrium display. Interior lighting should make desks and routes readable at night without making
every surface look uniformly lit. Review the default renderer first; shaders are an additional
compatibility pass.

## Information and characters

- At a distance, show status lamps, resident names and status dots. Show project and activity details
  nearby or when targeted. Keep a project label in its own spaced box; avoid labels obscuring monitors.
- Distinguish requests from errors. A waiting indicator can pulse gently; request text should remain
  calm and readable. Use errors for actual failures.
- Keep the chosen appearance consistent across chat, menus, world displays and resident labels.
  Identity colors name residents; status colors describe work.
- Residents must reflect their owning T3 conversations. Screenshots with synthetic activity are fixture
  evidence, never proof of live provider execution. The gaming cast must remain recognizable at normal
  playing distance, with clean skins and appropriate model widths.
- Native screens need deliberate spacing, readable secondary context, useful empty/loading/offline
  states, and visible controls at GUI scales 2, 3 and 4. Identify the owning machine when chats from
  different machines can otherwise look identical.

## Repeatable camera set

Run `node tools/t3-visual-qa.mjs <devPort> <fixturePort> <prefix/>` in an isolated modern HQ paired only
to the two protocol-2 loopback fixtures. See [developer checks](../tools/README.md). The runner refuses
real pairings, seeds activity only in fixtures, and verifies that capture dispatches no T3 commands.
The camera definitions are [tools/scenes/qa.json](../tools/scenes/qa.json).

1. Exterior at golden hour, showing architecture and landscape.
2. Entrance with the atrium summary and clear view into the campus.
3. The whole physical chat wall, with working, waiting and completed chats.
4. A resident working at its desk, with its own monitor readable.
5. A wide interior with multiple residents working.
6. The Decisions station with a pending native request.
7. Native chat with conversation, machine/project context and composer.
8. A native checkpoint review with multiple files and a selected patch.
9. Saved conversation library with search and owning-machine context.
10. Night exterior showing interior illumination through glass.

Compare before/after captures at the same angles. Inspect framing, label overlaps, text contrast,
lighting, character posture and whether the next action is understandable. Follow up with close desk
and crowded waiting views, and native screens at GUI scales 2/3/4. Use separate views for the upper
gallery, archive, usage room and garden when those areas change.

Record the build, Minecraft/Java versions, platform, fixture or live source, render configuration,
camera manifest and unresolved observations. Keep screenshots and machine-readable evidence under
ignored `artifacts/`; never capture private pairing links or transcripts for a public showcase.
