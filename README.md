# BLACKOUT

> The lights die. The door seals. The air starts running out. Your only teammate is an AI that has never seen the room.

BLACKOUT is a co-op escape room for [BOSS Console](https://bossconsole.ai), played by one human and one AI.

## The story

You wake up in a dark maintenance room with ten minutes of air left. You can see everything and touch everything, but you don't know how any of it works.

Your AI companion is somewhere on the other end of the line. It can't see a thing, but it has the manuals, a calculator and the door controls.

Talk to each other, and you both get out.

## Escaping the room

1. **Breakers.** You read the numbers off the panel. The AI works out the power and tells you which switches to flip.
2. **Cabinet.** You find a scrambled label. The AI knows the code to unscramble it. You type the password.
3. **Recorder.** Put three memory strips in order and find out what Mara and Ivo did the night the lights went out.
4. **Door.** You share the seal, the AI unlocks the channel, and you have 20 seconds to turn the handle.

## Rules

- **You** inspect, flip, type, order and turn. **The AI** reads manuals, calculates, routes power and unlocks the door.
- The AI only sees a clue after you press **Share**.
- You start with 600 seconds of air. When it runs out, the room closes.
- A wrong answer costs 15 seconds. A typo that isn't a real answer is free.
- You get three hints, and each one costs 20 seconds.
- Pausing freezes the clock, but the run counts as practice.
- If the door's 20 seconds run out, the AI can unlock it again.
- The AI has limited moves: 12 manual reads, 30 messages, 16 calculations, 8 power changes and 12 unlock attempts.
- Every room is different: the symbols, codes and story order change each time.

## Play

```powershell
.\gradlew.bat clean buildPluginJar
```

1. In BOSS, open **Toolbox → From File** and pick `build/libs/boss-plugin-blackout-0.4.0.jar`.
2. Open a **BLACKOUT** tab.
3. Press **Connect** to bring in your BOSS model, or connect any agent through the MCP tools below.

## AI tools

| Tool | What it does |
| --- | --- |
| `blackout_v2_observe` | See the room status, shared clues and messages |
| `blackout_v2_archive` | Read the manuals |
| `blackout_v2_message` | Talk to the human |
| `blackout_v2_calculate` | Add, subtract, multiply or divide |
| `blackout_v2_route` | Set power to LOW, NORMAL or HIGH |
| `blackout_v2_arm` | Unlock the exit channel |
