# Product brief for planning

> Historical combat brief. Implementation was approved, then the user replaced the
> default game with an escape room. Current scope and rules: ESCAPE-ROOM.md.
> No dated milestone schedule; progress is recorded through completion evidence in HANDOFF.

## User intent

Build a memorable, real BOSS hackathon contribution: a playable game for teams of
humans and heterogeneous AI agents, potentially usable at a campus competition.
The user likes BLACKOUT and wants a small extendable demo, not an unbounded game.
They requested repository setup followed by Plan Mode before implementation.

## Working concept, not specification

Two crews repair damaged stations and contest a shared relay. Humans operate a
visual board; agents interpret different information and take complementary actions
through the same versioned MCP interface regardless of provider. Neither role should
be ornamental. Equal equipment and budgets apply to both teams.

Start the discussion with one human and one agent per team. Decide whether a local
practice match is the first milestone and whether two-machine play is a submission
requirement. Do not silently substitute scripted opponents for real-agent support.

## Candidate experience

Observe -> communicate -> commit complementary orders -> simultaneous resolution ->
visible consequences -> adapt. Failure may come from a wrong diagnosis, bad resource
allocation, or an opponent's in-game disruption. Game code decides outcomes.

## Avoid

- Recreating a generic chat panel or workflow editor.
- Depending on unreleased host APIs or store credentials.
- An unrestricted 3D real-time multiplayer scope.
- Model-judged victory, invented benchmark scores, claims of perfect provider fairness.
- Making local files/seed secret by merely hiding fields in the UI.
- Treating a technical code-matching exercise as proof the game is fun.

## Sources already inspected

- https://bossconsole.ai/hackathon/
- https://github.com/risa-labs-inc/boss-plugin-arcade
- https://github.com/risa-labs-inc/boss-plugin-arcade/blob/main/CLAUDE.md
- https://github.com/risa-labs-inc/boss-plugins/blob/main/docs/creating-a-plugin.md
- Official contributor guide provided by the user; local copy in the Warden repo root.

Arcade has native Compose games, multiplayer Battleship and browser-backed poker.
This supports technical feasibility, not proof that BLACKOUT is implemented.
Existing agent game tools mean agent play alone is not our distinguishing feature.
Asymmetric cooperation has prior art, including Keep Talking and Nobody Explodes;
create original mechanics/assets and attribute inspiration honestly.
