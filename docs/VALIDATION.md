# Evidence and acceptance gates

## Measured

`./gradlew build` compiles the plugin and runs 35 tests, all passing, against rules 2.0.
Kotlin 2.3.0 on a Java 17 toolchain, compiled against `boss-plugin-api-1.0.87.jar`.

The suite covers:

- Circuit generation: every seed and round leaves a solvable route for both crews at both
  output levels, and both crews receive the same bus condition and the same set of routes.
- Compartment damage lands only on the named compartment, and a barrier absorbs only on the
  compartment it sits on.
- Phase order: barriers and repairs apply before both shots land; the repair test is
  constructed so that the surviving figure differs from the damage-first ordering.
- Damaged-compartment effects: reactor income, barrier cap and grid push each degrade.
- Victory: life support at zero, mutual loss as a draw, three grid points, and the
  round-limit tiebreak on grid points, then total integrity, then barriers.
- Determinism: a failing station bleeds the same compartment on a repeated resolution, and
  every recorded round re-resolves to the identical result from the replay file.
- Information split: the engineer view never contains the route map; the pilot view never
  contains the engineer's thermal or polarity and never carries exact rival figures.
- Command handling: stale rounds, duplicate and conflicting request IDs, locked orders,
  invalid routes, unaffordable pairs, budget exhaustion and a terminal match that stays
  terminal under every further call.
- Tool surface: malformed JSON, wrong types, unexpected keys, team and role selectors, and
  a stale match ID are all rejected; the gateway tool specifications are asserted identical
  to the MCP definitions so the in-app seat cannot be given an easier interface.
- Rival policies: across 200 seeds and all three difficulties, every generated order is
  affordable and legal; a veteran crew never misconfigures and a cadet crew sometimes does.

`./gradlew balanceReport` plays scripted crew against scripted crew across 2000 seeds per
pairing. The committed output is `docs/BALANCE.md`. It shows the intended skill ladder, all
three victory routes occurring, and matches ending before the round limit in the mixed
pairings. Mirror pairings of identical deterministic policies draw, which is expected and
is not evidence of balance.

## Not validated

- Enjoyment. No human has played this. There is no playtest, no onboarding study and no
  evidence about whether the crew channel is fun or merely obligatory.
- Live model play. The in-app engineer seat compiles and is wired to the host gateway, but
  no recorded session of a model holding the seat exists in this repository. Latency, cost,
  failure handling and provider differences are all unmeasured.
- MCP transport end to end. Tool handlers are tested directly, not through a real MCP client.
- BOSS lifecycle. Install, open, close, disable, re-enable, reload, missing configuration
  and disposal are unverified. The plugin JAR builds; that is all it proves.
- Persistence. `ProfileStore` degrades quietly when storage is absent, which is tested by
  construction but not against a real `PluginStorageProvider`.
- Everything about competitive integrity: networking, authentication, reconnect, identity
  binding and tournament operation. A local match is a trusted sandbox.

## Gates still to clear

1. An unfamiliar human completes onboarding and a full duel, and says what confused them.
2. A named model and version holds the engineer's seat for a full duel, with the transcript,
   latency and token cost recorded, including at least one failed and one recovered round.
3. The plugin installs into a running BOSS build and survives disable, re-enable and reload
   without leaking a registration or a coroutine.
4. A second model from a different provider plays the same seat with no prompt changes.
5. If remote play enters scope: an authoritative service, identity bound outside
   model-controlled tool arguments, and simultaneous submission handling.

Never report a gate as cleared on the strength of a passing unit test.
