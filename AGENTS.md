# Working on BLACKOUT

## Current phase: escape-room implementation

The user's latest direction supersedes the investigation as the default game: a minimal,
playable timed room for a human and an AI companion. Current design: restore power,
decode a cabinet password, reconstruct story fragments, coordinate the exit. See
`HANDOFF.md` first, then `docs/ESCAPE-ROOM.md`. Do not restore the combat design.

The investigation source and tests were removed in 0.4.0. The contract below is historical.

The user requested finishing code before compilation, with two compile attempts for this
iteration. Do not run repeated exploratory Gradle builds. See HANDOFF for the attempts used.

## Historical investigation contract

BLACKOUT is a co-op asymmetric investigation. A human pilot walks a dark station and sees
physical state; an AI archivist reads the station records and cannot see the station. The
fault is where a signed work order and the hardware disagree. Remote multiplayer and
publication stay outside scope.

The combat design (routes, barriers, compartments, relay points) was removed on
12 September 2026. Do not reintroduce it.

## Before changing the case generator

`engine/Case.kt` is the only place the solution exists. If you change it, `CaseTest` and the
solvability test in `InvestigationTest` are the contract:

- the archive must hold no internal contradiction, or the archivist solves it alone;
- the pilot must never be able to reach every compartment, or the archivist is optional;
- a scripted crew playing only through legal channels must still solve every seed.

## Scope and honesty

- This is a separate BOSS plugin candidate. Preserve unrelated host and Warden changes.
- Do not claim a built JAR means a tested plugin, or that a model response means a verified
  outcome.
- The in-app archivist seat is a real model playing through the real tools. Say which model.
  Never let a scripted fallback occupy that seat, and never imply one is an agent.
- Report exact tests run. Label dates estimated for milestones, provider compatibility and
  novelty claims according to evidence.
- Use the official BOSS plugin template and public API. Read current author docs,
  manifest/compatibility guidance and destination instructions before scaffolding runtime code.
- Do not copy proprietary plugin implementation without checking its license.
- Discuss substantial integration with maintainers through the prescribed proposal path.
- Do not launch BOSS in a blocking foreground process. The user controls the running app.
- No credentials in source, screenshots, case logs, debriefs or fixtures.
- A local trusted simulation is not an anti-cheat server. Team and role authorization must
  bind identity outside model-controlled tool arguments for competitive play.
- Do not give agents human-only actions. Naming the culprit is the pilot's call and there is
  deliberately no tool for it.

## UI

Seven colours, one accent, three type sizes, flat controls separated by space. One primary
thing on screen at a time. When adding an element, remove or demote another. The user's
13 September 2026 direction explicitly permits a Kotlin-rendered 3D room; keep accessible
2D controls for every interaction.

No remote repository has been created as part of this scaffold. Public integration, catalog
registration and store publication are distinct steps.
