# BLACKOUT: Rival Crews

A BOSS Console game for a human and an AI agent who each hold half the information and
have to talk to win. Two stations duel compartment by compartment during a blackout.

## The idea

Every station has four compartments: REACTOR, SHIELD ARRAY, RELAY MAST and LIFE SUPPORT.
Every shot names one of them, so an attack is a decision about what to take away from the
other crew rather than a number going down. Both crews commit in secret and the round
resolves at the same instant, which makes bracing a compartment a genuine read.

The split that makes it a two-player game:

| | Pilot (human) | Engineer (AI) |
|---|---|---|
| Power routes | sees all four | sees none |
| Own deck | exact figures | exact figures |
| Rival deck | ONLINE / DAMAGED / DOWN | exact integrity and barriers |
| Bus condition | nothing | thermal telemetry and one polarity scan |
| Chooses | action, route, compartment | thermal, polarity, output |

Neither half can restore a circuit alone. The pilot cannot pick a working route without
being told whether the bus is hot and whether polarity is inverted. The engineer cannot
know whether a route is high-output, and so cannot safely boost, without being told. A
wrong configuration still spends the energy, so the crew channel is the game.

## What is implemented

- Pure rules engine with seeded generation, simultaneous resolution and deterministic replay.
- Compartment targeting, per-compartment barriers, and damage effects that change play:
  a damaged reactor starves the crew, a damaged shield array halves the barrier cap, a
  damaged mast loses grid contests, and failing life support bleeds another compartment.
- Escalating damage from round three, so two careful crews cannot stall to the round limit.
- Three scripted rival crews. Measured outcomes are in `docs/BALANCE.md`.
- A Compose board: drawn power routes, clickable deck plans, crew channel, engineer console.
- Four versioned MCP tools (`blackout_v1_observe`, `_scan`, `_message`, `_commit`) with
  per-round budgets, idempotent request IDs and actionable error codes.
- An optional in-app engineer seat that drives the host AI gateway through those same tool
  definitions, so the game is playable without wiring up an external agent first. The
  provider, model, every tool call and the token count are shown on screen.
- Local crew record and replay persistence through plugin storage.

## What is not implemented or verified

- No remote multiplayer, no authoritative match service, no two-machine play.
- No human playtest and no enjoyment evidence. Balance numbers are scripted-crew only.
- No live model has been recorded playing the engineer's seat in this repository.
- Not loaded into a running BOSS build here; the plugin JAR builds but install, open,
  disable, reload and cleanup are unverified.
- A local duel is a trusted sandbox. It is not protected against anyone with access to
  this desktop, and it is not tournament infrastructure.

## Running it

```sh
./gradlew build            # compile, test, and produce the plugin JAR
./gradlew test             # 35 tests
./gradlew balanceReport    # regenerate docs/BALANCE.md
./gradlew runPrototype     # standalone Compose harness, no BOSS required
```

The plugin JAR lands in `build/libs/`. The manifest is `src/main/resources/META-INF/boss-plugin/plugin.json`.

## Documents

`docs/PLAN-BRIEF.md` holds the product brief, `docs/DECISIONS.md` the design decisions and
what is still open, `docs/ARCHITECTURE.md` the layout, `docs/VALIDATION.md` what has and
has not been tested, and `docs/BALANCE.md` the measured outcomes. Repository working rules
are in `AGENTS.md`.

Hackathon deadline supplied by the user: 20 September 2026, 23:59 IST.
Official brief: https://bossconsole.ai/hackathon/

This is a new plugin candidate, not a fork of Arcade and not an addition to Warden.
Asymmetric-information cooperation has prior art, including Keep Talking and Nobody
Explodes; the mechanics and assets here are original and the inspiration is stated.
