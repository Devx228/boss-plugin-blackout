# Decisions

## Superseding direction: escape room

The user requested a minimal, fun, timed human + AI escape room and explicitly permitted
improving the prototype's rules and UI. `ESCAPE-ROOM.md` is the current specification.
The default now has three linked puzzles and a cooperative door release. Physical clues
remain human-owned; manuals remain agent-owned. The investigation decisions below are
historical and still apply only if editing the retained investigation generator.

Decisions settled in code are recorded here with the reason and the trigger that would
reopen them. Anything under "Still open" is not decided.

## The redesign of 12 September 2026

BLACKOUT was a compartment-combat duel: four targetable compartments, per-compartment
barriers, power routes, thermal and polarity configuration, relay points, damage escalation
and a life-support bleed. It was dropped.

Why: the rules needed a 250-word in-game manual. A hackathon judge gives a game about three
minutes, and a player met that manual before they met the game. The information split
underneath it was sound, but it was dressed as a combat simulator and the conversation it
was supposed to force was buried.

What survived: the split itself, the MCP tool shape, the session and plugin shell.

## Settled

### 1. The honest asymmetry is that the agent cannot see the screen

The pilot walks the station and sees physical state. The archivist reads records and sees
none of it. Nothing here is artificially hidden from either seat; the agent genuinely
cannot look at a compartment, and the human genuinely cannot read the archive.

Why: every contrived split invites the question "why does the agent not just get told". This
one does not, because it is the literal situation.

Revisit if: a future host lets an agent read the rendered board.

### 2. Records are claims, the station is truth

Work orders say a job was signed off. Only the pilot can see whether it was actually done.

Why: it gives both seats something the other cannot derive, in a way a player understands
immediately without a manual. It also gives the agent real text reasoning to do rather than
a lookup, which is what current models are good at.

Revisit if: playtesters solve cases without ever discussing a work order.

### 3. Every signer was genuinely rostered where they signed

The archive contains no internal contradiction. A test asserts this over 400 seeds.

Why: the first draft let the roster and the work orders disagree, which meant the archivist
could solve the case alone and the pilot was decoration. A test now fails if that returns.

Revisit if: cases feel too hard, in which case loosen elsewhere, not here.

### 4. The pilot can never reach the whole station

Three to five walks against eight compartments. A test asserts this for every difficulty.

Why: it is what forces the first conversation. Without it the pilot searches everything and
the archivist is optional.

Revisit if: playtesters run out of walks before they have understood the case.

### 5. Two personal items, only one of them damning

The culprit's item sits in the faulty compartment while the roster places them elsewhere. A
second item sits somewhere its owner really was.

Why: one item alone would make the trace a giveaway. Two means the roster has to be
consulted, which means the archivist has to be consulted.

Revisit if: the decoy never fools anybody.

### 6. The badge reader network is down in every case

An archive record says personnel movement stopped being logged at 01:00.

Why: it is the in-fiction reason the archive cannot answer "who was where" on its own. A
player who asks the obvious question gets an answer from the world rather than a shrug.

Revisit if: never, unless the deduction changes shape.

### 7. Naming the culprit is not a tool

The agent can observe, search, message and mark. It cannot accuse. A test asserts no such
tool exists.

Why: the agent should persuade, not decide. It also keeps the human unambiguously the
player rather than a spectator watching a model solve a puzzle.

Revisit if: an agent-versus-agent mode enters scope, which would need its own surface.

### 8. The archivist's verdicts are drawn on the pilot's map

SUSPECT and CLEAR marks, each with a one-line reason, render on the compartment itself.

Why: it is the difference between a chat log and a game. The agent's reasoning becomes a
visible act on the shared board, which is also what makes the demo legible from across a
room.

Revisit if: marks turn out to replace the conversation rather than anchor it.

### 9. The in-app archivist seat is an option, never a substitute

The plugin can drive the host AI gateway as the archivist through tool specifications built
from the same MCP definitions an external agent gets. A test asserts the two are identical.
The seat is off by default, and the provider, model and tool calls are shown.

Why: requiring a hand-wired MCP agent before anything is playable made the game impossible
to demonstrate.

Revisit if: the host exposes per-agent identity.

### 10. Local practice only, trusted sandbox

One local seat shared by every attached caller. No identity binding, no authoritative
service.

Why: the shared BOSS registry cannot identify individual callers, so a role selector in a
tool argument would be decoration, not authorisation.

Revisit if: competitive play enters scope, which needs a server first.

## Still open

- No human has played a case. Every claim about pacing below is a guess.
- Battery length. Six, eight and ten minutes are untested against real model latency.
- Whether three signed jobs is the right number, or whether four makes a better middle game.
- Whether the undone-job clue and the personal item are too redundant. Either one alone can
  reach the answer, which is forgiving but may be too forgiving.
- Whether a forged archive record, where the archivist itself is being lied to, is worth
  building as a second act.
- Spectator and replay presentation beyond the stored debrief.
- Accessibility: no keyboard navigation and no screen reader pass.
