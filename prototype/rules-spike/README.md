# BLACKOUT: Rival Crews — temporary rules spike

Original Java 17 rules prototype, compatible with a future Kotlin/JVM plugin.
Preserved in the dedicated BLACKOUT planning repository. Nothing is published.
This is disposable experimental code, not the approved game design or plugin runtime.

## Run

```powershell
javac -d out Blackout.java BlackoutTest.java
java -cp out BlackoutTest
```

## Draft rules

Two equal crews start with 10 hull, 0 shield, 5 energy and 0 relay points.
Each round the human sees a circuit index; the agent sees a shuffled code manual.
They communicate to match the code to the circuit. Each commits exactly once.
Both crews commit before simultaneous resolution. Incorrect diagnosis spends the action.

- Repair: costs 2, restores 3 hull up to 10.
- Shield: costs 2, adds 3 shield up to 6; shields apply before this round's damage.
- Attack: costs 3, deals 4 damage absorbed by shields first.
- Capture: costs 2, earns 1 relay point only if the opponent did not also successfully capture.
- Regenerate 3 energy each round, capped at 8.
- End on destruction, 3 relay points, or 8 rounds. Destruction takes precedence;
  otherwise compare relay points, then hull; exact ties draw.
- Full order history becomes available after the match for deterministic replay.

These values are initial hypotheses, not playtested balance. Repair at full health is legal.
The simple code puzzle establishes the role split; it is not yet an interesting puzzle system.

## Boundaries

No BOSS adapter, UI, real model connection, network server, authentication, timeout,
persistence, tournament support or anti-cheat. Role views filter fields but callers can
request either role: authorization must live in a future authenticated adapter. Local
seed/state are not secret against a local agent. Test helpers intentionally see both roles.

All providers should eventually receive the same versioned tool schemas, legal moves,
resources and action limits. This does not equalize model intelligence, cost or latency.
Do not expose human commands or match administration through the agent-facing tool set.

Next: move into a standalone plugin repository, scaffold from the official template,
add a playable native board and agent MCP adapter, then validate with a real BOSS instance.
Discuss standalone-versus-Arcade fit with maintainers before substantial integration.
