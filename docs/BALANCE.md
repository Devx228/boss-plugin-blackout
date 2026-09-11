# Measured balance

Regenerate with `./gradlew balanceReport`. The figures below are the output of that task
against rules 2.0, committed so that any number quoted elsewhere can be checked.

What this measures: the three scripted crew policies playing each other across 2000 seeds
per pairing. What it does not measure: whether the game is enjoyable, how a language model
plays the engineer's seat, how a human pilot behaves under a clock, or anything about the
MCP transport. No human playtest has been run.

```text
BLACKOUT balance report, rules 2.0, 2000 seeds per pairing.
Scripted crew against scripted crew. No model and no human is involved.

PAIRING (blue vs orange)      BLUE%  ORANGE%  DRAW%  AVG ROUNDS  BY LIFE%  BY GRID%  TO ROUND 6%
CADET vs CADET                 43.3     42.6   14.2        3.03      96.1       0.0        3.9
CADET vs OPERATOR              27.6     72.2    0.3        5.56      23.7      34.0       42.4
CADET vs VETERAN               28.1     69.7    2.3        5.46      95.2       0.0        4.8
OPERATOR vs CADET              72.2     27.2    0.7        5.55      23.9      37.0       39.1
OPERATOR vs OPERATOR            0.0      0.0  100.0        6.00       0.0       0.0      100.0
OPERATOR vs VETERAN             0.0    100.0    0.0        6.00       0.0     100.0        0.0
VETERAN vs CADET               69.8     29.1    1.2        5.40      95.2       0.0        4.8
VETERAN vs OPERATOR           100.0      0.0    0.0        6.00       0.0     100.0        0.0
VETERAN vs VETERAN              0.0      0.0  100.0        6.00     100.0       0.0        0.0

Compartments left at zero integrity, across every pairing:
  REACTOR          10.8% of stations
  SHIELD ARRAY      0.5% of stations
  RELAY MAST        0.5% of stations
  LIFE SUPPORT     30.6% of stations

A pairing that reads close to 50/50 is symmetric, not balanced: both sides run
the same policy. The signal to read is whether a stronger policy beats a weaker
one, whether matches end before the round limit, and whether all three victory
routes occur. None of this is a substitute for a human playtest.


Transcript: OPERATOR (blue) vs VETERAN (orange), seed 0.
  R1 blue e6 g0 Grid(reactor=8, shields=8, relay=8, life=8) -> SHIELD LIFE NORMAL
     orange e6 g0 Grid(reactor=8, shields=8, relay=8, life=8) -> FIRE REACTOR NORMAL
  R2 blue e7 g0 Grid(reactor=4, shields=8, relay=8, life=8) -> RELAY RELAY NORMAL
     orange e6 g0 Grid(reactor=8, shields=8, relay=8, life=8) -> SHIELD REACTOR BOOST
  R3 blue e8 g1 Grid(reactor=4, shields=8, relay=8, life=8) -> RELAY RELAY NORMAL
     orange e6 g0 Grid(reactor=8, shields=8, relay=8, life=8) -> RELAY RELAY BOOST
  R4 blue e9 g1 Grid(reactor=4, shields=8, relay=8, life=8) -> RELAY RELAY NORMAL
     orange e6 g1 Grid(reactor=8, shields=8, relay=8, life=8) -> FIRE REACTOR BOOST
  R5 blue e7 g2 Grid(reactor=0, shields=8, relay=8, life=8) -> REPAIR REACTOR NORMAL
     orange e5 g1 Grid(reactor=8, shields=8, relay=8, life=8) -> RELAY RELAY BOOST
  R6 blue e6 g2 Grid(reactor=3, shields=8, relay=8, life=8) -> RELAY RELAY NORMAL
     orange e5 g2 Grid(reactor=8, shields=8, relay=8, life=8) -> RELAY RELAY BOOST
  Result ORANGE after round 6.
```
