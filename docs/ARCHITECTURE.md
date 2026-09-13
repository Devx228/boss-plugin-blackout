# Layout

## Current default: escape room

`engine/EscapeRoom.kt` generates private scenario content. `application/Escape.kt` owns
the synchronized game and separate pilot/agent views. `mcp/EscapeTools.kt` validates the
six v2 tools. `ui/EscapeBoard.kt` renders the responsive room, controls and channel;
`ui/RoomScene.kt` draws the flat room with accessible object buttons. The plugin and
standalone harness open EscapeBoard. Session owns escape lifetime, and Archivist drives
the optional real gateway companion with the same v2 definitions. Legacy investigation
files below are retained; its v1 tools are not registered in the default plugin.

See ESCAPE-ROOM.md and ../HANDOFF.md before changing either mode.

## Historical investigation layout

```
engine/Case.kt              Pure case generation. No Compose, no BOSS API, no I/O.
                            Seeded, deterministic, and the only place the solution exists.

application/Investigation.kt  One case in progress. Every boundary an agent can reach goes
                              through here. Holds the clock, the walk budget, the channel
                              and the verdict marks, and decides what each seat may see.
application/Session.kt        One local pilot seat plus one archivist seat.
application/Archivist.kt      Optional: drives the host AI gateway through the MCP tools.
application/Profile.kt        Local logbook and case debriefs through plugin storage.

mcp/BlackoutTools.kt        The four agent tools. Argument validation, error codes, and the
                            gateway specs built from the same definitions.

ui/Theme.kt                 Seven colours, one accent, three type sizes.
ui/StationMap.kt            The blueprint map. Line work on Canvas, berths as composables.
ui/BlackoutBoard.kt         The board: opening screen, map, findings, channel, overlays.

BlackoutPlugin.kt           Registration, tab type, lifecycle.
Prototype.kt                Standalone Compose harness, no BOSS required.
```

## The one rule

`engine/Case.kt` holds the answer. `Investigation` is the only thing that reads it, and it
never lets a physical state, a map coordinate or the solution reach an archivist view. The
tests in `InvestigationTest` and `ToolsTest` serialize those views and assert it.

Everything an agent can do is a method on `Investigation`. `BlackoutTools` is a thin,
validating translation layer over those methods and holds no game state of its own.
