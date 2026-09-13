# BLACKOUT

> The lights are dead, the air is running out, and your only teammate is an AI that has never seen the room.

BLACKOUT is a co-op escape room for [BOSS Console](https://bossconsole.ai). **You** can see and touch everything. **Your AI companion** has the manuals, a calculator and the door controls. Nobody gets out alone.

## A run in 10 minutes

1. **Breakers.** You read the load and voltage off the panel. The AI does the maths, sets remote power and tells you the switch order.
2. **Cabinet.** You see a scrambled label. The AI knows the cipher shift. You type the password together.
3. **Recorder.** Put three memory strips in the right order and find out what Mara and Ivo really did that night.
4. **Door.** You share the seal, the AI arms the channel, and you get 20 seconds to pull the handle.

## Rules

- **Split roles.** Only you inspect objects, flip breakers, type the password, order the strips and turn the handle. Only the AI reads the manuals, calculates, routes power and arms the door.
- **Share on purpose.** The AI sees nothing until you press **Share** on a clue.
- **Air.** You start with 600 seconds. When it hits zero, the room closes, even if a penalty caused it.
- **Mistakes.** A wrong but well-formed answer costs 15 seconds. Wrong power trips the breakers and a wrong channel denies release, each for 15 seconds too. A typo that isn't a real answer is free.
- **Hints.** You get three, and each costs 20 seconds. Nothing ever locks you out for good.
- **Pause.** Pause freezes both clocks but marks the run as practice.
- **Door.** Release stays armed for 20 seconds. If it expires, the AI can arm it again.
- **Budgets.** The AI gets 12 archive reads, 30 messages, 16 calculations, 8 power settings and 12 arm attempts per room.
- **Real or nobody.** The companion is a real model or absent. There is no scripted fake.
- Every room is seeded, so symbols, priorities, cipher and strip order change each run.

## Play it

```powershell
.\gradlew.bat clean test buildPluginJar
```

1. In BOSS, open **Toolbox → From File** and pick `build/libs/boss-plugin-blackout-0.4.0.jar`.
2. Open a new **BLACKOUT** tab.
3. Hit **Connect** to use your configured BOSS model, or attach any agent over MCP.

No BOSS handy? `.\gradlew.bat runPrototype` opens the room standalone.

## Agent tools

| Tool | What the AI can do |
| --- | --- |
| `blackout_v2_observe` | See the status, clues you shared and messages |
| `blackout_v2_archive` | Read the manuals |
| `blackout_v2_message` | Talk to you |
| `blackout_v2_calculate` | Add, subtract, multiply or divide |
| `blackout_v2_route` | Set remote power to LOW, NORMAL or HIGH |
| `blackout_v2_arm` | Authorize the exit channel |

The AI can't press your buttons, and you can't read its manuals. That's the game. Every call has a budget and is tied to the current room.

## Status

- Built against `boss-plugin-api` 1.0.89 with JDK 17, tested on Windows 11.
- Unit tests solve 100 seeded rooms using only what each side can legally see.
- Not yet playtested by humans or a live model. Balance numbers are first guesses.
- No network calls of its own. The built-in companion uses whatever model you configured in BOSS.

Details live in [the room spec](docs/ESCAPE-ROOM.md), [validation notes](docs/VALIDATION.md) and [the handoff](HANDOFF.md).

## Credits

Made for fun for the BOSS Console hackathon. Inspired by *Keep Talking and Nobody Explodes*. All puzzles, story and art are original.
