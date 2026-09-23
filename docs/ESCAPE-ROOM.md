# BLACKOUT — the maintenance room

BLACKOUT is a timed room for a human and a real AI remote-systems companion. The human sees and touches the room. The companion reads manuals and operates isolated infrastructure. Neither role can complete any stage alone.

## Play loop

1. The human shares fitted symbols and electrical readings. The companion calculates current and routes power; the human sets the breaker order.
2. The human shares the powered cabinet label and serial plate. The companion looks up the offset for the plate's prefix, tunes the decoder and derives the word; the human enters it.
3. The human shares the waveform and memory strips. The companion synchronizes the recorder; the human orders the cause-and-effect timeline.
4. The human shares the exit seal. The companion arms its channel for 20 seconds; the human turns the handle.

Every remote action waits until the human has shared that stage's clue (`CLUE_NOT_SHARED`, no time cost), so neither seat can finish a stage alone.

Four seeded incident packs change the characters, emergency, story, passwords and environmental discoveries without changing these rules.

## Timing

Standard mode has 600 seconds, 15-second mistakes and 20-second hints. Showcase mode has 240 seconds, 10-second mistakes and 10-second hints. Both have three hints and no permanent lockouts. Invalid input has no time cost. Pause freezes room and authorization timers and marks the run as practice.

## Remote systems

The companion may route power, tune the decoder, synchronize the recorder and arm the exit only at their corresponding stages. After power restoration it may also control lighting and ventilation. Ultraviolet light and the incident's safe ventilation mode reveal optional physical details to the human. Ultraviolet also makes the next hint free, and the safe ventilation mode recovers air once (30 seconds in Standard, 15 in Showcase, capped at the starting reserve). Incorrect valid remote settings apply the current mode's normal penalty and remain recoverable.

Nine `blackout_v3_*` tools provide observe, archive, message, calculate, route power, tune decoder, synchronize recorder, control environment and arm exit. Every mutation has an idempotent request ID and current room ID. Strict schemas, budgets, rate limits, stale-room rejection and terminal immutability apply. There is no agent tool for a human action.

## UI

The room is a lightweight Kotlin 3D scene rendered with perspective projection in Compose Canvas. Power, lights, ventilation, cabinet doors, recorder, remote circuits and exit visibly respond to accepted game events. Standard Compose controls remain available for every interaction, so the projected scene is never the only input mechanism. Sound is local and optional; reduced motion disables camera drift and ambient motion.

## Honesty and security

The built-in seat uses the configured BOSS gateway and displays its provider/model. An external MCP seat is explicitly unverified. There is no scripted agent fallback. The shared local endpoint cannot establish separate agent identity and is not competitive anti-cheat. Debriefs exclude credentials, conversation free text, model reasoning and private solutions.

## Acceptance

Tests must solve every incident through legal role views, prove each stage requires an agent and human action, validate both timing modes, exercise all remote failures and retries, and serialize agent views to check privacy. A successful JAR build is not evidence of a fun room, live model behavior or BOSS lifecycle correctness; those require named-model and human playtests.
