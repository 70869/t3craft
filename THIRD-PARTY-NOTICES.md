# Third-party code

AgentCraft HQ, blocks, cast, textures and studio presentation originate from
[blendi-remade/agentcraft](https://github.com/blendi-remade/agentcraft), under the root MIT license.

The native T3 connection, chat, model, decision, activity, diff and pin panels in
`mod/src/client/java/dev/agentcraft/client/t3` are adapted from
[maxwellyoung/t3craft](https://github.com/maxwellyoung/t3craft), copyright 2026 Maxwell Young,
under [its MIT license](licenses/t3craft-client-MIT.txt). Its separate office, villagers,
world-command MCP server and self-test automation are not included. T3Craft connects this
client to AgentCraft's existing HQ characters instead.
The imported source reference is commit `56676d33fb24e12ce0ef22cb048753c8c2a78056`.
The fixture server and adapted smoke checks carry the same MIT attribution.

T3 Code itself is developed at [pingdotgg/t3code](https://github.com/pingdotgg/t3code).
This mod uses its paired client HTTP/RPC protocol. The built-in theme color snapshot in
`assets-src/t3-themes.json` is adapted from `packages/shared/src/themePalettes.ts` at commit
`250e052f44dd313b658abebc707242a7b25be340`, copyright 2026 T3 Tools Inc., under
[the T3 Code MIT license](licenses/t3code-MIT.txt), included in the JAR. OKLCH colors are
converted to sRGB for Minecraft. The mod does not redistribute the T3 Code application.

Eighteen NPC looks are adapted from [Minetest Skins Pack 1](https://opengameart.org/content/minetest-skins-pack-1)
by isaiah658, released under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).
The original 64x32 skins are converted to Minecraft's modern 64x64 atlas by mirroring the
legacy arm and leg textures for the left limbs. Credit is appreciated but not required.
