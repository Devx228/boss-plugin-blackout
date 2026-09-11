# Structure

```text
src/main/kotlin/.../blackout/
  engine/       Rules.kt      pure state, phases, seeded generation, victory
                Rival.kt      scripted crew policies, playable as either side
                Balance.kt    headless harness behind ./gradlew balanceReport
  application/  Match.kt      round lifecycle, budgets, per-seat views, replay
                Session.kt    the single local seat
                EngineerCrew.kt  optional host-gateway agent in the engineer seat
                Profile.kt    crew record and replay persistence
  mcp/          BlackoutTools.kt  four versioned tools, shared with the gateway seat
  ui/           Theme.kt      palette and shared panel primitives
                CircuitArt.kt drawn power routes
                StationArt.kt deck plan, compartments, grid meter
                CrewConsole.kt engineer console and crew channel
                BlackoutBoard.kt layout, briefing, order flow, result
  BlackoutPlugin.kt  BOSS registration and disposal
  Prototype.kt       standalone Compose harness
src/test/kotlin/.../blackout/
docs/
prototype/rules-spike/   disposable Java spike, superseded
```

## Dependency direction

`engine` depends on nothing but kotlinx-serialization. It has no Compose, no BOSS API and
no model-provider types, so it can move behind a match service later without edits.

`application` depends on `engine` and, only in `EngineerCrew`, on the BOSS AI gateway types.
`ui` depends on `application` and `engine`. `mcp` depends on `application`.

## The seat boundary

`Match` is the only place command boundaries serialize. Three views come out of it:

- `humanView()` carries the route map, the pilot's own deck in full, the rival deck as
  coarse status, and whether the engineer has locked.
- `observe()` carries the full sensor sweep of both decks, the bus condition and the scan,
  and no route map at all.
- `replay()` carries every round's orders and frames, and no crew messages.

Anything that widens one of these views is a game design change, not a convenience.

## The tool surface

`BlackoutTools.tools()` builds the four MCP definitions. `aiTools()` maps those same
definitions to the gateway's `AiToolSpec`, so the in-app engineer seat and an external MCP
agent see identical names, descriptions and schemas. A test asserts it.

Both paths land in `BlackoutTools.call`, which rejects unexpected keys, wrong types, stale
match IDs and any team or role selector before touching the match.

## Not built

No `server/` module. Remote competition needs deployment, authentication, persistence and
identity binding decided first, and none of that is decided. The engine being free of UI and
host types is what keeps that option open, not a promise that it will be taken.
