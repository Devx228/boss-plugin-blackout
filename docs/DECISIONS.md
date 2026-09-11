# Decisions

Decisions that are settled in code are recorded here with the reason and the trigger that
would reopen them. Anything under "Still open" is not decided.

## Settled

### 1. The human's unique capability is the deck and the route map

The pilot sees all four power routes and their properties, and chooses the action, the
route and the compartment. The engineer sees none of it.

Why: the original design gave the human a button and the agent a lookup table. Splitting
the circuit so that neither side can validate a route alone makes the conversation the
mechanic rather than decoration.

Revisit if: playtesters report the pilot is just reading a table aloud.

### 2. The agent's unique capability is the sensor sweep and the bus condition

The engineer reads exact integrity and barrier figures for both stations, plus the required
thermal setting and one polarity scan per round. The pilot sees only ONLINE, DAMAGED or
DOWN on the rival deck.

Why: it gives the agent a judgement to make, not a value to echo. Deciding which rival
compartment is worth hitting, and whether the crew's own life support is one hit from
failing, needs the numbers the pilot does not have.

Revisit if: models reliably report the sweep verbatim instead of drawing a conclusion. The
fallback is to hand the agent raw readings and put the threshold in the manual.

### 3. The engineer's locked configuration is hidden from the pilot

The pilot sees that the engineer has locked, and sees the output level because it changes
what the order costs, but never the thermal or polarity.

Why: while the pilot could read thermal and polarity off a locked order, the correct play
was to wait for the engineer to commit and skip the conversation entirely. Hiding it makes
the message mandatory.

Revisit if: crews stall waiting for each other rather than talking.

### 4. Four compartments, not one hull

REACTOR drives energy income, SHIELD ARRAY caps barriers, RELAY MAST drives the grid push,
LIFE SUPPORT ends the match. Barriers are per-compartment.

Why: a single hull bar made every attack identical. Naming a compartment turns an attack
into a choice about what the other crew loses, and per-compartment barriers make defence a
read on the opponent's intent rather than a flat damage reduction.

Revisit if: one compartment dominates. `docs/BALANCE.md` currently shows the reactor and
life support taking most of the destruction and the mast almost none, which is a known
imbalance to watch.

### 5. Damage escalates from round three

Fire damage rises by one at round three and again at round five.

Why: measured. Before escalation, every pairing of competent scripted crews ran the full
six rounds and was decided on the tiebreak, with life support destroyed in 6% of stations.
After, mixed pairings end early and life support is destroyed in 31%.

Revisit if: matches start ending before crews have had a chance to use the deck.

### 6. The rival crew is scripted, deterministic and openly labelled

Three difficulties, seeded from the match seed, never reading the player's pending orders
or route map, and generalised to play either side so the balance harness can measure them.

Why: a second live model doubles cost and latency for a local practice match. The rival is
labelled as scripted everywhere it appears; it is never presented as agent play.

Revisit if: two-crew play enters scope. The policy code is already team-agnostic.

### 7. The in-app engineer seat is an option, never a substitute

The plugin can drive the host AI gateway as the engineer, through tool specifications built
from the same MCP definitions an external agent gets. A test asserts the two are identical.
The seat is off by default, and the provider, model, tool calls and token count are shown.

Why: requiring a hand-wired MCP agent before anything is playable made the game impossible
to demonstrate. Building the specs from the MCP definitions stops the in-app seat quietly
getting an easier interface.

Revisit if: the host exposes per-agent identity, at which point the seat should bind to one.

### 8. Local practice only, trusted sandbox

One local seat shared by every attached caller. No identity binding, no authoritative
service.

Why: the shared BOSS registry cannot identify individual callers, so any team or role
selector in a tool argument would be a decoration, not authorisation. The UI and the manual
both say the sandbox is trusted rather than protected.

Revisit if: competitive play enters scope, which requires a server first.

## Still open

- Whether remote two-crew play is in submission scope at all, and what the fallback demo is.
- Whether to propose this to the Arcade maintainers or keep it a standalone plugin.
- Round length. Two minutes is untested against a real model's latency plus a human reading
  the deck.
- Message budgets. Six each per round is a guess.
- Spectator and replay presentation beyond the round list.
- Accessibility beyond colour independence: no keyboard navigation or screen reader pass.
- Whether the relay mast pulls its weight, given how rarely it is destroyed.
