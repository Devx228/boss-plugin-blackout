# BLACKOUT handoff

## Start here

Workspace: `D:\boss-plugin-blackout`. The user approved implementation and then changed
the product direction to a minimal, fun human + AI escape room. They want the whole
experience built, not another planning-only response. No dated milestone schedule.
The submission deadline supplied by the user remains 20 September 2026, 23:59 IST.

Read `AGENTS.md`, `docs/ESCAPE-ROOM.md`, and the newest section of `docs/VALIDATION.md`.
Older combat and investigation plans are historical. **Do not reintroduce combat.**

## Current implementation

Version 0.4.0 is the escape-room prototype. Its default BOSS tab and standalone harness
open `ui/EscapeBoard.kt`. The 0.3 investigation source and tests were removed.

- `engine/EscapeRoom.kt`: seeded original content: six-symbol manual, three fitted
  breakers, Caesar-encoded cabinet word, three shuffled narrative strips.
- `application/Escape.kt`: synchronized authoritative local state, stage prerequisites,
  explicit human-to-agent clue sharing, injected monotonic clock, 15-second wrong-answer
  penalties, three 20-second hints, pause marked as practice, terminal debrief, idempotent
  agent messages/authorization, and final human + agent handshake.
- `mcp/EscapeTools.kt`: exactly six `blackout_v2_*` tools; typed argument validation,
  stale room rejection, no human actions, same specs for external MCP and host AI gateway.
- `application/Companion.kt`: real-model gateway integration with an
  escape companion prompt and bounded manual memory. No scripted agent fallback. Uses
  human activity revision to avoid responding to its own messages or losing a human move
  during an in-flight turn. Model output must reach the human via the message tool.
- `ui/RoomScene.kt`: original flat line-art room drawn in Compose Canvas, with semantic
  keyboard-focusable object buttons. Power, cabinet and exit visibly change with progress.
- `ui/EscapeBoard.kt`: responsive room + channel layout; focused object interaction;
  click-to-order breakers/story; password input; connection setup; inspect/share;
  timed final handle; hints/pause; inventory; log; ending and persistent debrief.
- `application/Session.kt`: new escape lifetime, reset/cancellation and old-room protection.
- Plugin registers the six v2 tools only. Legacy v1 tools were removed with the investigation.

## Build/test status for this iteration

The user requested **at most two compilation attempts**, after coding is finished.

1. First attempt: `./gradlew.bat --offline --no-daemon --max-workers=1 test buildPluginJar`.
   Main source compiled. Test compilation failed on two incorrect test references:
   `engine.EscapeStage` (actually in application) and `AiToolSpec.parametersJson`
   (correct property: `inputSchema`). Both references were corrected in source.
2. Final attempt, same command: BUILD SUCCESSFUL. `EscapeTest` 16 tests, 0 failures, 0 errors,
   0 skipped. Produced `build/libs/boss-plugin-blackout-0.4.0.jar`.

Do not mistake an existing JAR or old 40-test report for validation of the new escape room.
The new `EscapeTest` currently contains 16 named tests, including legal-channel solutions
for 100 seeds, timer/pause/final authorization, invalid input, privacy, budgets, and tools.
No live model or human fun/pacing test has been completed for v0.4.0.

## How to continue

1. Done: final compile/test pass recorded above and in VALIDATION.
2. If permitted, run the standalone headful `smokeUi` harness and inspect its render.
   That is a UI render check, not a human/agent playtest. Never launch the BOSS host.
3. User-controlled BOSS test: install the built 0.4.0 JAR through Toolbox, open BLACKOUT,
   configure the real companion, complete a room, test close/reopen, disable/re-enable,
   reload, gateway interruption, and persistence. Capture provider/model and real evidence.
4. Have unfamiliar humans play. Tune time, clue wording and puzzle satisfaction based on
   their behavior. Automated solvers do not establish enjoyment.

## Known limitations / remaining work

- Ten minutes, penalties and hint budget are initial design values, not playtested balance.
- Built-in companion inherits the configured gateway. Requires real provider validation.
  External agent setup is self-reported in the opening screen, not a connection probe.
- Explore-without-agent is explicitly an incomplete exploration mode; no fake companion.
- This chapter has three authored puzzle families with seeded variations. Story premise is
  fixed. More chapters, richer branching, sound, remote co-op and competitive play are not
  implemented and not required for the first playable.
- Debriefs store events, not user/model free-text, provider credentials, or model reasoning.
  Persistence is attempted for terminal runs while the UI is mounted; abrupt process death
  is not crash recovery. Do not promise a resumable save or full replay system.
- Only the agent has the terminal calculator (16 calls, four bounded arithmetic operations).
  Human supplies load/voltage readings; agent calculates amps and configures remote power
  (eight settings). Human breaker operation fails until this is done. Final authorization also
  requires the agent to select a channel using the human's door seal and agent's manual.
- Companion arming can be repeated 12 times; observe is once/second; archive 12 queries;
  messages 30 per room. Shared BOSS endpoint is one trusted seat, not agent identity.
- No security against local file/screen access. Human actions are absent from agent tools.
- Legacy docs contain historical runtime evidence for 0.2.0, explicitly not this version.
- Existing README/VALIDATION edits were present on entry; their prior evidence is preserved.

## Boundaries

No host or Warden changes. Source is published at https://github.com/Devx228/boss-plugin-blackout. No catalog
registration or store publication has been requested. Do not use `installPlugin` blindly: it copies into the user's live
plugin directory; prefer user-controlled Toolbox installation. Never delete existing JARs
or stop Java processes to work around a build issue.
