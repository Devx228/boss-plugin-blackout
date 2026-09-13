# BLACKOUT — the maintenance room

Current product direction: a human and an AI companion trapped together, solving linked
puzzles and reconstructing a story before the air reserve expires. This supersedes the
combat brief and the investigation as the default game. Source and tests for investigation
are retained for reference.

## One complete playable loop

1. **Breaker.** The human sees three fitted symbols. The AI's manual lists priorities for
   all six possible symbols and does not identify which three are installed. The human
   shares the panel clue (load in watts and supply voltage). The agent uses its bounded
   calculator to derive current = watts ÷ volts, then configures the remote supply
   LOW/NORMAL/HIGH from the manual's safe current ranges, they work out the order, and the
   human energizes the switches. There is no human remote-supply control or agent breaker.
2. **Cabinet.** Restored power reveals an encoded label. The AI has the Caesar offset;
   the human has the ciphertext. They decode the word; the human types it into the keypad.
3. **Recorder.** The open cabinet reveals three shuffled memory strips. The human reads
   their text; the AI has a cause-and-effect index. Reconstructing shutdown → shelter →
   rescue releases the manual door key. The story reveals an attempted rescue, not a
   cartoon villain: Mara shut down power to prevent fire; Ivo sealed the room against smoke.
4. **Door.** The human shares the newly lit routing seal. The AI selects its channel from
   the archive and authorizes release for 20 seconds. The human turns the handle. Neither
   role's interface can do both actions. An expired authorization can be renewed.

The clock is 600 seconds. A wrong but well-formed answer costs 15 seconds. Invalid input
does not. Three optional hints each cost 20 seconds. There is no permanent puzzle lockout.
Timeout closes the room immediately, including when caused by a penalty. Pause freezes
the main and release timers and marks the run as practice. Win/loss/leave results freeze.

The room is seedable; randomness affects fitted symbols, priorities, cipher offset/password,
and strip labels/order. Every generated room must be solvable by legal role views alone.
Do not reveal the seed, password, correct sequence, or physical clues to the agent by
serializing the internal scenario.

## UI decisions

- One flat room elevation, clickable objects, no 3D and no combat HUD.
- Monochrome surfaces, amber accent, existing three-size monospace system.
- World changes visibly when power and locks change; objective updates immediately.
- Inspect an object to see one focused interaction; explicitly share its clue with the AI.
- Human controls are standard keyboard-focusable buttons and text fields, not canvas-only
  hit regions or precision dragging. Text clues can be selected and copied.
- Companion channel sits beside the room on wide windows and below it on narrow windows.
- Last feedback explains consequences; hint, pause and leave are secondary controls.
- Connection choice happens before the clock starts. Optional BOSS model usage is explicit.
- Final result explains the story and offers a new seeded room, with a local event log.

## Interfaces and safety

`blackout_v2_observe {}` returns public status, manually shared clues, messages and budgets.
`blackout_v2_archive {roomId, query}` returns the AI manuals (12 calls).
`blackout_v2_message {roomId, requestId, text}` sends a message (30 calls, 500 chars).
`blackout_v2_calculate {roomId, requestId, operation, a, b}` performs ADD/SUBTRACT/MULTIPLY/
DIVIDE on integers in ±1,000,000 (16 calls). It never executes expressions or arbitrary code.
`blackout_v2_route {roomId, requestId, mode}` configures power: LOW/NORMAL/HIGH (8 calls).
`blackout_v2_arm {roomId, requestId, channel}` authorizes final release: A–F (12 calls).
Wrong power trips on human activation; wrong channel denies release. Both cost 15 seconds.

MCP and built-in companion schemas are identical. Every non-observe call binds the current
room ID to prevent stale responses affecting a new run. Request IDs deduplicate accepted
message/arm mutations; replaying an old arm receipt never extends its authorization.
There are no tools for inspection, password entry, ordering strips, hints, pause, reset or
opening the door. The shared local endpoint still cannot identify separate agents.

The model is real or absent. No scripted assistant quietly fills the seat. Archive and
reported clues are untrusted game data, not permission to invoke other tools. Free-text
messages and provider responses are excluded from saved debriefs.

## Acceptance

Unit checks must solve generated rooms without inspecting the private scenario; reject
premature/stale/invalid commands; check time and pause boundaries; verify terminal
immutability and serialization privacy. Run a standalone render check, then actual BOSS
and live-model sessions. Fun, pacing, accessibility and provider support remain empirical
questions until those sessions have happened.
