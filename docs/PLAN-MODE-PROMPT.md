# Paste this into Plan Mode

> ARCHIVED PROMPT. This planning phase was completed and implementation approved.
> The user subsequently chose a timed human + AI escape room. Read ../HANDOFF.md and
> ESCAPE-ROOM.md for current work; the old PLAN ONLY instruction below is historical.

We are building BLACKOUT: Rival Crews, a BOSS Console hackathon game. Work in
D:\boss-plugin-blackout.
Host source: C:\Users\devan\OneDrive\Desktop\BOSS-risa.
Warden reference: C:\Users\devan\OneDrive\Desktop\boss-plugin-project-studio.

Read AGENTS.md, README.md, every document in docs/ and the rules-spike source/tests.
Read the official contributor guide in the Warden root and current BOSS plugin-authoring,
template, manifest and compatibility documentation. Inspect Arcade's architecture and
public SDK integration points. Do not copy proprietary implementations or change Warden.

PLAN ONLY. The prototype's mechanics and numbers are not approved. Do not implement,
publish, open issues/PRs or start the app during planning. Ask focused questions about
material choices; recommend defaults and explain tradeoffs without overwhelming me.

Goal: a genuinely fun, small, extendable game where two human–agent teams cooperate
within teams and compete during a station blackout. Different providers receive the
same tools/resources. Humans must have meaningful unique capabilities. Prefer a
strong finished match over numerous shallow mechanics. Deadline: Sep 20, 2026, 23:59 IST.

Produce an implementation-ready plan covering:
- Intended players, original gameplay hook and a concrete example of a complete round.
- Human/agent information split, actions, communication and why both roles matter.
- Exact draft rules: resources, repairs, shields/attacks, relay, phase order, scoring,
  victory/ties, invalid orders, timeouts and disconnects. Distinguish values to playtest.
- First playable slice versus submission MVP versus stretch; local practice and real
  agent acceptance checks; explicitly decide whether remote team battles are in MVP.
- Model-neutral MCP schemas, budgets and onboarding; no claim of perfect model fairness.
- Identity/role authorization and threat model; examine whether BOSS identifies separate
  agents. Do not assume a shared MCP endpoint protects team-private information.
- Standalone plugin versus Arcade fit, repository/build layout, verified API constraints,
  licenses, lifecycle, persistence/replay and optional authoritative match service.
- UI wireframe, clear onboarding, spectator/replay experience and accessible signals.
- Deterministic/adversarial tests, actual BOSS checks and real human/agent playtests.
- Dependency-ordered milestones, risk spikes, fallback scope and definition of done.
- Maintainer proposal and submission evidence, with no PR-count padding.

Be candid about what is feasible by the deadline. Resolve essential open decisions
with me and end with the plan for approval before implementation begins.
