# Layout

## Current default: escape room

`engine/EscapeRoom.kt` generates three private incident packs and seeded puzzle content.
`application/Escape.kt` owns the synchronized game, modes, remote systems, typed effects,
and separate pilot/agent views. `mcp/EscapeTools.kt` validates the nine v3 tools.
The pilot view also carries a `timeline` of messages and companion activity, plus the
finished room's epilogue. Session owns room lifetime; Companion drives the optional real
gateway model through the same v3 definitions.

```
ui/EscapeBoard.kt     Screen state and layout; routes every human action to Escape.
ui/RoomScene.kt       The illustrated room on a 1600-wide virtual stage: lighting, machinery,
                      hover and hit areas, effects, and the escape and trapped cinematics.
ui/SceneKit.kt        Drawing primitives: metal, glow, light cones, smoke, sparks, cables.
ui/Devices.kt         The four puzzle close-ups: breakers, cabinet keypad, recorder, door.
ui/CompanionPanel.kt  Remote systems, conversation and the companion's activity log.
ui/Hud.kt             Stage stepper, air gauge, hints, pause, log, sound, leave, feedback.
ui/Opening.kt         Title screen, companion connection and mode choice.
ui/Ending.kt          Timed ending: room cinematic, epilogue, title, debrief, credits.
ui/Overlays.kt        Modals, companion setup and the pocket log drawer.
ui/Theme.kt           Colours, type and the shared button, tag and lamp components.
ui/Glyphs.kt          Line-art interface icons and the six puzzle symbols.
ui/Clues.kt           Lifts numbers and symbols out of object descriptions for drawing.
ui/Effects.kt         Locally generated sound cues and ending sound.
Snapshot.kt           Offscreen renderer for ./gradlew renderUi; not shipped in the JAR.
```

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
