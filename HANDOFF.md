# BLACKOUT handoff

## Current direction

Workspace: `D:\boss-plugin-blackout`. Version 0.6.0 is a human plus AI escape room with an active companion that controls machinery. The room is an illustrated 2D scene with depth, lighting and animation; the earlier projected 3D room was replaced at the user's request. Do not restore combat or the historical investigation.

Read `AGENTS.md`, `docs/ESCAPE-ROOM.md`, and the newest section of `docs/VALIDATION.md` before changing behavior.

## Changed in 0.6.0 (rules `escape-3`)

- Remote actions return `CLUE_NOT_SHARED` (no time cost) until the human shares that stage's clue. The companion can no longer brute-force channels or offsets without the human.
- The cabinet offset comes from a serial plate only the human sees, looked up in a six-prefix table only the companion has. Before this the companion could tune the decoder alone.
- Safe ventilation recovers air once (+30 s Standard, +15 s Showcase, capped at the starting reserve); UV makes the next hint free. The optional systems now matter.
- A fourth incident, SURGE, and six passwords per incident instead of three.
- No UI files changed. `Snapshot.kt` reads the new cabinet manual. EscapeTest runs 20 tests.

## Implemented in 0.5.0

- `engine/EscapeRoom.kt`: four seeded incident packs with characters, emergencies, passwords, endings, environmental discoveries, and an escape and a trapped epilogue for each.
- `application/Escape.kt`: Standard and Showcase modes, authoritative state, required human and agent actions in every stage, remote systems, hints, recoverable penalties, effects and privacy-safe debriefs. The pilot view carries a `timeline` of messages and companion activity (calculations, routing, tuning, syncing, lighting, airflow, arming, archive reads by record name only) and the finished room's `epilogue`.
- `mcp/EscapeTools.kt`: nine strict `blackout_v3_*` tools with room binding, idempotency, budgets and no human actions. The agent-facing JSON is unchanged by the activity timeline.
- `application/Companion.kt`: the configured BOSS model plays through the same tools. There is no scripted fallback.
- `ui/`: `EscapeBoard` owns state and layout; `RoomScene` + `SceneKit` draw the room, effects and ending cinematics; `Devices` holds the four puzzle close-ups; `CompanionPanel` shows systems and the chat with the companion work log; `Hud`, `Opening`, `Ending`, `Overlays`, `Theme`, `Glyphs`, `Clues` and `Effects` complete the interface.
- `Snapshot.kt` and `./gradlew renderUi`: renders every screen offscreen to `build/ui-snapshots/` with no window. Excluded from the plugin JAR.

## Build and test evidence

- `.\gradlew.bat --offline --no-daemon --max-workers=1 test renderUi buildPluginJar` passes on JDK 17 and Windows 11. EscapeTest runs 17 tests, 0 failures.
- The newest test checks that companion activity reaches the pilot timeline, that archive activity reveals no manual contents, that replayed requests do not duplicate activity, and that epilogues appear only after the run ends.
- Offscreen renders of the opening, all four stages, the compact layout, the escape story, credits, the trapped sequence and the debrief were inspected.

## Live evidence

- The 0.5.0 JAR loads in a BOSS 9.5.12 development build (`~/.boss_debug`): all eight persisted plugins load, and the nine v3 tools are listed on the local `boss` MCP server.
- An external agent played the companion seat over MCP in five rooms: three escapes (one with two mistakes, two with none) and two trapped runs, one lost and one left to time out. Activity rows, the escape cinematic and the trapped cinematic were watched in BOSS by the user.

## Next useful work

- Play with the built-in BOSS model seat and with a second provider; note latency and token use.
- Have unfamiliar players try Standard and Showcase; tune timings and clue wording from what they do.
- Consider making Share clue more prominent: early players asked the companion before sharing.

## Boundaries

No host or Warden changes. Do not put credentials, conversation free text or model reasoning in logs or debriefs. The shared local MCP endpoint is one trusted seat and cannot authenticate separate agents. Catalog registration and store publication are maintainer decisions.
