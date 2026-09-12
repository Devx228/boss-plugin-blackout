# BLACKOUT

A BOSS Console game for one human and one AI agent. Main power fails, and the two of you
hold different halves of the reason why.

## The idea

Station Kepler-9 went dark at 02:14. You are aboard with a torch. Your archivist is in the
records room and cannot see a single thing you see.

| | Pilot (human) | Archivist (AI) |
|---|---|---|
| The station itself | walks it, sees what is physically there | nothing |
| Shift roster | nothing | every entry |
| Signed work orders | nothing | every entry |
| Telemetry, comms, supply | nothing | every entry |
| Compartments reachable | three to five of eight | not applicable |
| Names the culprit | yes | never |

Three compartments had work signed off during the night. Two of those jobs were really
done. One was signed off and never carried out, and that is why the lights are out.

The archive records what people **claimed**. The station shows what is **true**. The fault
is the one place those cannot both be right, and neither seat can find it alone. The
archive cannot, because every signer was genuinely rostered where they signed. The pilot
cannot, because they can reach only a few compartments and have no idea which ones matter
until the archivist tells them.

Somebody left a personal item in the faulty compartment, and the roster puts that person
somewhere else entirely. That mismatch names the culprit. A second personal item is lying
somewhere its owner really was rostered, and it means nothing. Only the roster tells the
two apart, and only the pilot can see either of them.

## What is implemented

- Seeded case generator with a deterministic, verified solution.
- A test that plays 200 cases through the legal channels only and solves every one.
- Tests asserting the split is real: the archivist view never carries a physical state the
  pilot has not reported, the map, or the answer.
- Four versioned MCP tools (`blackout_v1_observe`, `_archive`, `_message`, `_mark`) with
  budgets, idempotent request IDs and actionable error codes. There is deliberately no tool
  that names the culprit; that call belongs to the human.
- The archivist can draw SUSPECT and CLEAR verdicts onto the pilot's map, so the agent's
  reasoning is visible on screen rather than buried in a chat log.
- A Compose board: blueprint station map, findings, crew channel, three difficulties.
- An optional in-app archivist seat driving the host AI gateway through those same tool
  definitions, so the game is playable without wiring an external agent first.
- Local crew record and case debriefs through plugin storage.

## What is not implemented or verified

- No remote multiplayer and no two-machine play.
- No human playtest. Nobody outside this repository has played a case.
- No live model has been recorded holding the archivist's seat here. The seat compiles and
  is wired to the gateway; latency, cost and provider differences are unmeasured.
- Not loaded into a running BOSS build. The plugin JAR builds; install, open, disable,
  reload and cleanup are unverified.
- A local case is a trusted sandbox, not tournament infrastructure.

## Running it

```sh
./gradlew build        # compile, run 40 tests, produce the plugin JAR
./gradlew test         # tests only
./gradlew runPrototype # standalone Compose harness, no BOSS required
./gradlew smokeUi      # render the board headfully for a few seconds and exit
./gradlew installPlugin # copy the JAR into the BOSS plugins directory (close BOSS first)
```

The plugin JAR lands in `build/libs/`. The manifest is
`src/main/resources/META-INF/boss-plugin/plugin.json`.

## Documents

`docs/PLAN-BRIEF.md` holds the product brief, `docs/DECISIONS.md` the design decisions and
what is still open, `docs/ARCHITECTURE.md` the layout, and `docs/VALIDATION.md` what has and
has not been tested. Repository working rules are in `AGENTS.md`.

Hackathon deadline supplied by the user: 20 September 2026, 23:59 IST.
Official brief: https://bossconsole.ai/hackathon/

This is a new plugin candidate, not a fork of Arcade and not an addition to Warden.
Asymmetric-information cooperation has prior art, including Keep Talking and Nobody
Explodes; the mechanics and assets here are original and the inspiration is stated.
