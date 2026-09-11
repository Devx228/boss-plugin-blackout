# Working on BLACKOUT

## Current phase: playable local prototype

Rules 2.0 is implemented and tested: four targetable compartments per station, simultaneous
resolution, three scripted rival crews, a Compose board, four MCP tools and an optional
in-app engineer seat driven by the host AI gateway. Remote multiplayer and publication stay
outside scope.

Before changing the rules, run `./gradlew balanceReport` and regenerate `docs/BALANCE.md`.
Balance claims in this repository are expected to be reproducible from that task.

## Scope and honesty

- This is a separate BOSS plugin candidate. Preserve unrelated host and Warden changes.
- Do not claim a built JAR means a tested plugin or a model response means a verified outcome.
- The in-app engineer seat is a real model playing through the real tools. Say which model.
  Never let a scripted fallback occupy the engineer's seat, and never imply one is an agent.
- Report exact tests run. Label balance numbers, dates estimated for milestones,
  provider compatibility and novelty claims according to evidence.
- Use the official BOSS plugin template and public API. Read current author docs,
  manifest/compatibility guidance and destination instructions before scaffolding runtime code.
- Do not copy proprietary plugin implementation without checking its license.
- Discuss substantial integration with maintainers through the prescribed proposal path.
- Do not launch BOSS in a blocking foreground process. The user controls the running app.
- Do not run Gradle builds without checking whether they could disrupt the user's live BOSS build.
- No credentials in source, screenshots, game logs, replay or fixtures.
- A local trusted simulation is not an anti-cheat server. Team/role authorization must
  bind identity outside model-controlled tool arguments for competitive play.
- Do not give agents human-only actions or administrative state through convenience tools.

No remote repository has been created as part of this scaffold. Public integration,
catalog registration and store publication are distinct steps.
