# BLACKOUT

> The lights die. The door seals. Your only teammate is an AI that cannot see the room.

BLACKOUT is a cooperative escape-room plugin for [BOSS Console](https://bossconsole.ai). A human explores a dark, reactive maintenance room while a real AI companion reads records and controls remote machinery.

## The rule

You have eyes and hands. Your companion has records and remote control. Neither can escape alone.

1. **Power:** share the panel; the AI calculates and routes power; you operate the breakers.
2. **Cabinet:** share the label; the AI tunes the decoder; you enter the decoded word.
3. **Recorder:** share the waveform and strips; the AI synchronizes the channel; you rebuild the timeline.
4. **Exit:** share the seal; the AI arms the release; you turn the handle within 20 seconds.

After power returns, the companion can also control lighting and ventilation. These optional systems reveal environmental story details and visibly change the room.

## What you will see

- **A room that reacts.** Lights come on, smoke drifts, sparks fly on a mistake and the pressure door slides open when you both get it right.
- **Your AI at work.** Every calculation, power route, decoder tune and door authorization appears in the chat as it happens, next to the conversation.
- **An ending that takes its time.** Escape and the camera pushes into the light, tells you what really happened that night and rolls the credits. Run out of air and the room dies around you, reminds you that you are still inside, and rolls them in red.

## Modes

- **Standard:** 10 minutes; mistakes cost 15 seconds; hints cost 20 seconds.
- **Showcase:** 4 minutes; mistakes and hints cost 10 seconds.
- Three hints are available. Pausing freezes both timers and marks the run as practice.
- Every valid mistake is recoverable. Invalid tool input never costs game time.

## Play

```powershell
.\gradlew.bat clean buildPluginJar
```

1. In BOSS, open **Toolbox → From File** and select `build/libs/boss-plugin-blackout-0.5.0.jar`.
2. Open a **BLACKOUT** tab.
3. Select **Connect** to use the configured BOSS model, or attach one external agent through MCP.

The companion seat is always a real configured model or an external agent. BLACKOUT has no scripted solver fallback.

## Agent tools

| Tool | Remote capability |
| --- | --- |
| `blackout_v3_observe` | Read status, shared clues, messages and available actions |
| `blackout_v3_archive` | Search manuals and incident records |
| `blackout_v3_message` | Speak through the room terminal |
| `blackout_v3_calculate` | Perform bounded arithmetic |
| `blackout_v3_route_power` | Route LOW, NORMAL or HIGH supply power |
| `blackout_v3_tune_decoder` | Physically tune the cabinet decoder |
| `blackout_v3_sync_recorder` | Synchronize the memory recorder |
| `blackout_v3_control_environment` | Operate room lighting and ventilation |
| `blackout_v3_arm_exit` | Authorize the exit channel |

There are no agent tools for seeing the room, operating breakers, entering passwords, arranging strips, pausing, resetting, requesting hints or turning the handle.
