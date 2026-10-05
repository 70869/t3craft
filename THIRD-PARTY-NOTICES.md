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
This mod uses its paired client HTTP/RPC protocol; it does not redistribute T3 Code.
