# Evidence and acceptance gates

## Current escape-room iteration (0.4.0)

The default game is now the escape room described in ESCAPE-ROOM.md. Historical evidence
below is preserved and must not be applied to 0.4.0.

First compile attempt: main source compiled; test compilation failed on two incorrect
references in EscapeTest (EscapeStage package and AiToolSpec inputSchema property). Those
references have been corrected in source. Final build/test attempt passed on JDK 17,
Windows 11: EscapeTest 16 tests, 0 failures, 0 errors, 0 skipped, including 100 seeded
legal-channel escape solutions. No human or real-model room playtest,
BOSS 0.4.0 load/lifecycle check, or 0.4.0 persistence check has been performed.

See HANDOFF.md for the user's two-compilation limit and the next-agent checklist.

## Historical investigation evidence (0.3.0)

## Measured

`./gradlew build` compiles the plugin and runs 40 tests, all passing. Kotlin 2.3.0 on a
Java 17 toolchain, compiled against `boss-plugin-api-1.0.89.jar`.

The suite covers:

- **Case generation over 400 seeds.** Exactly one fault and one culprit; the culprit is
  never the signer; exactly two personal items, one of them the culprit's; exactly three
  signed work orders; and every signer rostered where they signed, so the archive holds no
  internal contradiction.
- **The split is real.** The archive alone cannot pick the fault, because all three work
  orders are identical in shape. The station alone cannot name the culprit, because the two
  personal items are indistinguishable without the roster. No difficulty lets the pilot
  reach all eight compartments.
- **Solvability, end to end, over 200 seeds.** A scripted crew reads the archive, walks only
  the compartments the archive points at, reports what it finds, applies the roster rule and
  accuses. Every case is solved. The test never reads the private solution.
- **Determinism.** The same seed rebuilds the same case; different seeds move both the fault
  and the culprit.
- **Information boundaries.** The archivist view never carries a physical state the pilot has
  not reported, never carries map coordinates, and never carries the debrief. Reporting a
  finding is the only thing that moves it across.
- **Command handling.** Walk budget, report-before-walk, closed cases staying closed under
  every further call, the battery expiring, pause and resume, observe rate limiting, archive
  search budget, message budget and validation, duplicate and conflicting request IDs.
- **Tool surface.** Malformed JSON, wrong types, unexpected keys, seat and role selectors,
  unknown tools and oversized argument blobs are all rejected. There is no tool that names
  the culprit. The gateway tool specifications are asserted identical to the MCP definitions,
  so the in-app seat cannot be handed an easier interface.

`./gradlew smokeUi` opens the board headfully, opens a case and exits, which turns "does it
draw" into a pass or fail rather than a screenshot somebody has to look at. It passes.

**The plugin has loaded into a running BOSS build, at the previous version.** The host's own
records show it. `~/.boss_debug/plugins/installed.json` registers
`ai.rever.boss.plugin.dynamic.blackout` at version 0.2.0, enabled, installed 12 September
2026. `~/.boss_debug/mcp-calls.jsonl` records 14 tool calls against that provider on the same
evening: eight `observe`, two `scan`, two `message` and two `commit`, with the early calls
erroring and later ones succeeding. That is evidence that registration, the MCP provider and
the agent-facing tool surface work inside the host. It says nothing about version 0.3.0,
which has not been installed, and nothing about disable, reload or disposal.

## Not validated

- **Enjoyment.** No human has played a case. There is no playtest, no onboarding study and
  no evidence about whether the conversation is fun or merely obligatory.
- **Live model play.** The in-app archivist seat compiles and is wired to the host gateway,
  but no recorded session of a model holding the seat exists in this repository. Latency,
  cost, failure handling and provider differences are all unmeasured. The system prompt has
  not been tested against a real model.
- **MCP transport end to end.** Tool handlers are tested directly, not through a real client.
- **BOSS lifecycle for this version.** Version 0.3.0 has not been loaded into a running
  BOSS build. Close, disable, re-enable, reload and disposal are unverified at any version.
- **Persistence.** `ProfileStore` degrades quietly when storage is absent, which is tested by
  construction but not against a real `PluginStorageProvider`.
- **Difficulty.** The walk and battery numbers are guesses. Nothing has been tuned against a
  human or a model.
- Everything about competitive integrity: networking, authentication, reconnect and identity
  binding. A local case is a trusted sandbox.

## Gates still to clear

1. An unfamiliar human opens a case, finishes it, and says what confused them.
2. A named model and version holds the archivist's seat for a full case, with the transcript,
   latency and token cost recorded, including at least one recovered failure.
3. The plugin installs into a running BOSS build and survives disable, re-enable and reload
   without leaking a registration or a coroutine.
4. A second model from a different provider plays the same seat with no prompt changes.
5. Difficulty numbers revised against evidence from 1 and 2 rather than taste.

Never report a gate as cleared on the strength of a passing unit test.
