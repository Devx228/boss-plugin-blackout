# BLACKOUT

BLACKOUT is a two-player cooperative escape-room game that runs as a tab plugin inside [BOSS Console](https://bossconsole.ai). One player is a human. The other is an AI companion: either the AI model configured in BOSS, or any external agent connected through BOSS's MCP server.

The two players have different abilities and information:

| | Human | AI companion |
| --- | --- | --- |
| Sees the room and its objects | Yes | No |
| Physical actions (breakers, keypad, strips, door handle) | Yes | No |
| Reads the manuals and incident records | No | Yes |
| Remote systems (power routing, decoder, recorder, lighting, airflow, exit release) | No | Yes |
| Calculator | No | Yes |
| Can send messages | Yes | Yes |

The companion only learns what is in the room when the human presses **Share clue** on an object. Neither player can finish a stage alone.

## Contents

- [Requirements](#requirements)
- [Build and install](#build-and-install)
- [Starting a game](#starting-a-game)
- [Connecting a companion](#connecting-a-companion)
- [Screen layout](#screen-layout)
- [How a room is played](#how-a-room-is-played)
- [Stage 1: Power](#stage-1-power)
- [Stage 2: Cabinet](#stage-2-cabinet)
- [Stage 3: Recorder](#stage-3-recorder)
- [Stage 4: Exit](#stage-4-exit)
- [Optional environment controls](#optional-environment-controls)
- [Time, mistakes, hints and pause](#time-mistakes-hints-and-pause)
- [How a room ends](#how-a-room-ends)
- [Results and saved data](#results-and-saved-data)
- [MCP tool reference](#mcp-tool-reference)
- [Built-in companion behaviour](#built-in-companion-behaviour)
- [Content reference](#content-reference)
- [Development](#development)
- [Project layout](#project-layout)
- [Tests](#tests)
- [Compatibility](#compatibility)
- [Access, privacy and data](#access-privacy-and-data)

## Requirements

- BOSS Console with plugin API 1.0.89 (tested on a BOSS 9.5.12 development build).
- JDK 17 to build. Gradle is provided by the wrapper.
- `libs/boss-plugin-api-1.0.89.jar` (included). Another path can be passed with `-PbossApiJar=<path>`.
- For the AI companion: either an AI provider configured in BOSS, or an MCP-capable agent (Claude Code, Codex, Gemini CLI or similar).

## Build and install

```powershell
.\gradlew.bat clean test buildPluginJar
```

On macOS or Linux use `./gradlew clean test buildPluginJar`. The plugin JAR is written to `build/libs/boss-plugin-blackout-0.5.0.jar`.

Install it in BOSS:

1. Open **Toolbox → From File** and select the JAR.
2. Enable the plugin if BOSS asks.
3. Open the new tab dialog and choose **BLACKOUT**, then **Play**. The tab opens with no input.

Plugin manifest: `src/main/resources/META-INF/boss-plugin/plugin.json`. Plugin ID `ai.rever.boss.plugin.dynamic.blackout`, type `tab`, dynamic and unloadable.

## Starting a game

The BLACKOUT tab opens on the title screen. It shows the premise, the three roles, a companion card and the mode choice.

1. **Companion card.** Shows whether a companion is ready.
   - **CONNECT** opens the Companion window (see [Connecting a companion](#connecting-a-companion)).
   - The switch **I've attached an external agent** marks that an external MCP agent is playing. It is shown when the built-in model is not enabled.
2. **Choose your air.** Pick **Standard** (10:00) or **Showcase** (4:00).
3. **ENTER THE ROOM** starts a room. It is enabled once the built-in model is enabled or the external agent switch is on.
4. **EXPLORE ALONE** starts a room without a companion. The human can look around, but no remote system will ever be operated, so the room cannot be escaped.

Starting a new room ends any previous room in the same tab session.

## Connecting a companion

Use exactly one companion per room. The game has a single companion seat, and the local MCP endpoint cannot tell two agents apart.

### Option A: the AI model configured in BOSS

1. On the title screen press **CONNECT**, or press **LINK** in the companion panel during a room.
2. Turn on **Use my BOSS model**. The switch is available only when BOSS provides an AI gateway. The window shows the provider and model.
3. Close the window and start a room.

Using the built-in model calls the configured provider and may cost tokens. See [Built-in companion behaviour](#built-in-companion-behaviour).

### Option B: an external agent over MCP

BOSS exposes plugin tools on its local MCP server named `boss`, hosted by the terminal-tab plugin on `127.0.0.1` (default port 7677, the next free port if taken). BLACKOUT registers nine tools there, which agents see as `mcp__boss__blackout_v3_*`.

1. In BOSS open **Toolbox → MCP**. Attach your agent CLI (one click for supported CLIs), or add the server URL shown there to your agent's MCP configuration, for example:
   ```
   claude mcp add --scope user --transport sse boss <url>
   codex mcp add boss --url <url>/mcp
   gemini mcp add boss <url> --transport sse --scope user
   ```
2. In the same tab make sure all nine `blackout_v3_*` tools are enabled.
3. On the BLACKOUT title screen turn on **I've attached an external agent** and start a room.
4. Give the agent this prompt (also shown, selectable, in the Companion window):
   > Play my BLACKOUT remote companion. Call blackout_v3_observe, read ALL records with blackout_v3_archive, operate the remote systems, and send me each next step with blackout_v3_message.
5. External agents are not notified of changes. The agent must keep calling `blackout_v3_observe` (at most once per second) to see new clues and messages.

See [MCP tool reference](#mcp-tool-reference) for the full contract and `docs/AGENT-QUICKSTART.md` for a step-by-step sequence.

## Screen layout

On wide tabs (1100 dp and wider) the screen has four areas. Narrower tabs stack them vertically in a scrolling column.

**Top bar**
- BLACKOUT wordmark.
- Stage stepper: POWER, CABINET, RECORDER, EXIT. The current stage is highlighted.
- Hint lamps (remaining hints) and the mistake count.
- Air gauge: remaining time and a draining bar. It turns red and pulses under one minute, and shows PAUSED · PRACTICE while paused.
- Buttons: **HINT n** (hints left), **PAUSE / RESUME**, **LOG** (pocket log drawer), **SOUND / MUTED**, **LEAVE** (asks for confirmation).

**Room** (main area)
- An illustrated maintenance room with four interactive objects: breaker panel (left), cipher cabinet (centre right, with the memory recorder inside), pressure door (right). The companion terminal on the desk shows a waveform that animates while the companion is working.
- Hovering an object shows its name. Clicking it selects it and opens its device below. The cabinet shows LOCKED · NO POWER until power is restored.
- The room reflects the game state:
  - Lights come on with power.
  - Lighting mode changes colour, and ultraviolet shows hidden writing on the wall.
  - The vent fan speed and the smoke change with airflow.
  - The cabinet doors open at the recorder stage, and the recorder reels spin once synchronized.
  - The door release lamp and countdown ring light up while the exit is armed.
  - Sparks, a red flash and shake mark a mistake. A pulse runs along the cable to the terminal when a clue is shared.
- The current objective is pinned top left. The latest feedback line (including penalties and "not allowed yet" messages) appears at the bottom of the room.

**Device dock** (under the room)
- A header with the object name, a CURRENT tag on the objective's object, a one-line instruction, four object buttons (breaker, cabinet, recorder, door) and **SHARE CLUE** (or SHARED).
- The selected object's device. Devices for finished stages show SOLVED. Locked objects show LOCKED. The door shows SEALED before the exit stage. When a stage is completed the dock opens the next object automatically.

**Companion panel** (right)
- Header with the companion orb, the connection label, and **CONNECT** or **LINK**.
- Remote systems readout: POWER, DECODER, RECORDER, RELEASE (seconds left), LIGHTS, AIRFLOW. The current stage's system is outlined.
- Conversation timeline, oldest first:
  - Human messages on the right. Shared clues are marked CLUE SENT.
  - Companion messages on the left.
  - Companion activity cards: one card per tool call, with a title and a result (for example `CALCULATE 384 ÷ 48 = 8`, `ROUTE POWER · Remote supply set to HIGH`, `TUNE DECODER · Offset 4 locked`, `SYNC RECORDER · Locked on channel A`, `SET LIGHTS · ULTRAVIOLET`, `SET AIRFLOW · INTAKE · air clearing`, `ARM RELEASE · Channel F armed · 20s`, `READ MANUALS · Power manual · Cabinet manual …`, `CHECKED THE ROOM`). Rejected actions are red and show the penalty.
- A "Companion is working" indicator while the built-in model is busy, or for a few seconds after an external agent's last tool call.
- Two stage-specific quick replies, and a message box (Enter sends, Shift+Enter adds a line, 500 characters maximum).
- For the built-in model: tool call and token counters and a **NUDGE** button that resets the companion's turn state.

**Pocket log** (LOG button): puzzles solved, mistakes, hints, every clue sent to the companion, items carried (Emergency power, Three memory strips, Manual release key), discoveries, and after the room ends, the event timeline.

## How a room is played

Each room is generated from a random seed. The seed chooses:
- one of three incidents,
- the fitted breaker symbols and manual priorities,
- load and supply values,
- the cipher word and shift,
- the waveform and channel tables,
- the strip labels and order,
- the door seal and exit channel table.

The rooms always have the same four stages, in order. A stage's objects and remote systems cannot be used before it.

For every object the human must:
1. Select the object (in the room or with the object buttons). This inspects it and reveals its clue text in the device.
2. Press **SHARE CLUE** to send that clue text to the companion. Nothing reaches the companion otherwise.

The companion reads the manuals once (`archive` with `ALL`), then answers each shared clue with a remote action and a message.

## Stage 1: Power

**Human sees** (breaker device): three fitted symbols as levers, a LOAD meter in watts, a SUPPLY meter in volts, and REMOTE SUPPLY lamps LOW / NORMAL / HIGH.

**Companion does:**
1. Calculates current: amps = watts ÷ volts.
2. Routes remote power from the power manual: 1–6 A → LOW, 7–12 A → NORMAL, 13–18 A → HIGH.
3. Tells the human the breaker order: the three fitted symbols in ascending priority, using the priority list in the power manual (each of the six symbols has a priority from 1 to 6).

**Human does:** clicks the three levers in the given order (they fill slots 1, 2, 3; **RESET** clears) and presses **ENERGIZE**. ENERGIZE is enabled only when three levers are chosen and remote power has been routed.

**Results:**
- Power not routed yet → rejected, no penalty.
- Wrong supply mode → the supply trips, mistake penalty.
- Wrong order → the breaker trips, mistake penalty.
- Correct → power restored, stage 2 begins, the room lights up and the cabinet unlocks.

## Stage 2: Cabinet

**Human sees** (cabinet device): the encoded label as letter tiles, the decoder dial with the current offset, a password field and an A–Z keypad.

**Companion does:**
1. Tunes the decoder to the offset in the cabinet manual (1–5). A wrong offset is a mistake penalty and can be retuned.
2. Decodes the label by moving each letter back by the offset, wrapping Z to A, and messages the word.

**Human does:** types the word (letters only, up to 12, shown in capitals) with the keyboard or keypad (⌫ deletes, CLR clears) and presses **UNLOCK** or Enter. UNLOCK is enabled once the decoder has been tuned.

**Results:**
- Decoder not at the correct offset → rejected, no penalty.
- Wrong word → the keypad rejects it, mistake penalty.
- Correct → the cabinet opens, stage 3 begins and the recorder becomes available.

## Stage 3: Recorder

**Human sees** (recorder device): the waveform symbol, channel lamps A–F, three timeline slots (FIRST, THEN, FINALLY) and three memory strips labelled A, B and C, each a line spoken by someone during the incident.

**Companion does:**
1. Finds the waveform symbol in the recorder index channel table and synchronizes that channel (A–F). A wrong channel is a mistake penalty and can be corrected.
2. Tells the human the strip order. The recorder index gives the causal order: the safety shutdown, then the shelter response, then the rescue plan.

**Human does:** clicks strips to fill FIRST, THEN, FINALLY (click a filled slot to take a strip back, **RESET** clears) and presses **PLAY BACK**. PLAY BACK is enabled when all three slots are filled and a channel is synchronized.

**Results:**
- Wrong or missing channel → rejected, no penalty.
- Wrong order → the recorder cannot reconcile it, mistake penalty.
- Correct → the manual release key is released and stage 4 begins.

## Stage 4: Exit

**Human sees** (door device): the routing seal symbol, the REMOTE RELEASE ring and state, and **TURN THE HANDLE**.

**Companion does:** finds the seal in the exit manual channel table and arms that channel. A correct channel arms the release for 20 seconds. A wrong channel is a mistake penalty and disarms. When the 20 seconds pass, the release disarms and can be armed again.

**Human does:** presses **TURN THE HANDLE** while the release is armed. It is disabled while the release is not armed or the room is paused.

**Result:** turning the handle while armed ends the room as ESCAPED.

## Optional environment controls

After power is restored the companion can use `control_environment`. These controls are optional; they never skip a stage.

| System | Settings | Effect |
| --- | --- | --- |
| LIGHTING | EMERGENCY, WORK, ULTRAVIOLET | Changes the room light. ULTRAVIOLET reveals hidden inspection ink on the wall (discovery 1 of 2). |
| VENTILATION | INTAKE, EXHAUST, HOLD | Only the setting named for the incident in the ENVIRONMENT record is safe. The safe setting clears the air and reveals a detail (discovery 2 of 2). Any other setting is a mistake penalty. |

Discoveries appear in the room, in the pocket log and in the results.

## Time, mistakes, hints and pause

| | Standard | Showcase |
| --- | --- | --- |
| Air | 10:00 | 4:00 |
| Mistake penalty | 15 s | 10 s |
| Hint penalty | 20 s | 10 s |

- **Mistakes** are wrong but well-formed answers, from either player: wrong supply mode, breaker order, word, strip order, decoder offset, recorder channel, exit channel or ventilation setting. Each subtracts the penalty from the remaining air. There is no lockout.
- **Invalid input** (missing prerequisites, malformed values, wrong stage) is rejected with an explanation and costs no time.
- **Hints:** three per room. Each gives a context-sensitive tip for the current stage and costs the hint penalty.
- **Pause** freezes the air timer and the exit release timer, and marks the run as practice for the rest of the room. The door cannot be opened and the exit cannot be armed while paused. When the BLACKOUT board is closed or disposed during a room, the room is paused.
- **Leave** ends the room as INTERRUPTED after confirmation.
- If the air reaches zero, including through a penalty, the room ends immediately as FAILED.

## How a room ends

When a room ends, the tab plays an ending sequence. **SKIP ›** (top right) or Esc jumps straight to the results.

**ESCAPED** (about 30 seconds):
1. The door slides open and the view moves towards it into warm light.
2. The incident's five-line escape epilogue types out over a light background.
3. Title: YOU MADE IT. BOTH OF YOU.
4. Results screen.
5. Rolling credits naming the companion (provider and model, or "An agent over MCP").
6. **PLAY AGAIN** and **MAIN MENU** appear.

**FAILED** (about 30 seconds):
1. The lights flicker out, the red beacon strobes, smoke thickens and the edges close in with static. A glitching AIR 0% appears, and two glints blink in the door porthole.
2. The incident's five-line trapped epilogue types out in red on black, over a heartbeat sound.
3. Title: YOU ARE STILL IN HERE.
4. Results screen titled THE ROOM KEPT YOU.
5. Credits in red.
6. **TRY AGAIN** and **MAIN MENU** appear.

**INTERRUPTED:** a short two-line epilogue, the title UNTIL NEXT TIME., then the results and buttons.

PLAY AGAIN / TRY AGAIN starts a new room in the same mode. MAIN MENU returns to the title screen.

## Results and saved data

The results screen shows:
- The outcome and mode.
- The room's ending text.
- The cooperation rating.
- TIME, AIR LEFT, MISTAKES, HINTS, CLUES SHARED, REMOTE ACTIONS and DISCOVERIES.
- A PRACTICE note if the room was paused.
- The last 14 timeline events, with time, stage and incident.

Cooperation rating:
- **SYNCHRONIZED:** escaped with 0 mistakes, 0 hints, at least 4 clues shared and at least 4 remote actions (power routes, decoder, recorder and environment controls, and arms).
- **COORDINATED:** escaped otherwise.
- **INCOMPLETE:** not escaped.

When a room ends, its debrief is saved to BOSS plugin storage under `escape.<roomId>`. The debrief contains:
- room ID, seed and mode,
- incident, outcome and seconds used,
- mistakes, hints, clues shared, remote actions and discoveries,
- rating and practice flag,
- the companion type (BUILT_IN with provider and model, EXTERNAL_MCP, or NONE),
- the event timeline.

It never contains chat text, model output or credentials. Sound mute is stored under `settings.muted`, and a reduced-motion preference is read from `settings.reducedMotion`.

## MCP tool reference

All tools are prefixed `blackout_v3_`. Every call returns a JSON text result in one of two shapes:

```json
{"schemaVersion":3,"ok":true,"data":{}}
{"schemaVersion":3,"ok":false,"code":"STALE_ROOM","message":"Observe the current room first.","category":"SESSION","retryable":true}
```

General rules:
- Arguments must be a JSON object with exactly the listed keys, and no more than 4096 characters.
- Every tool except `observe` requires `roomId`, which must be the current room (otherwise `STALE_ROOM`). With no room open, calls return `NO_ROOM`.
- Mutating tools require `requestId`: 1–64 characters from `A–Z a–z 0–9 _ -`. Repeating a `requestId` with the same arguments returns the original receipt without acting again. Reusing it with different arguments returns `REQUEST_CONFLICT`.
- Receipts are `{"roomId","revision","detail"}`.

| Tool | Arguments | Budget per room | What it does |
| --- | --- | --- | --- |
| `observe` | none | at most once per second | Returns the room state (below). Read-only. |
| `archive` | `roomId`, `query` (1–120 chars) | 12 | `ALL` returns all six records. Any other query returns records containing that text (case-insensitive). Records: POWER MANUAL, CABINET MANUAL, RECORDER INDEX, EXIT MANUAL, ENVIRONMENT, INCIDENT. |
| `message` | `roomId`, `requestId`, `text` (1–500 chars, plain text) | 30 | Posts a companion message in the human's chat. |
| `calculate` | `roomId`, `requestId`, `operation` (`ADD` `SUBTRACT` `MULTIPLY` `DIVIDE`), `a`, `b` (integers, ±1,000,000) | 16 | Returns the result in the receipt detail. Division returns a decimal and rejects zero. |
| `route_power` | `roomId`, `requestId`, `mode` (`LOW` `NORMAL` `HIGH`) | 8 | Sets remote supply. Power stage only. |
| `tune_decoder` | `roomId`, `requestId`, `shift` (1–5) | shares 16 controls | Sets the decoder. Cabinet stage only. Wrong offset costs a penalty. |
| `sync_recorder` | `roomId`, `requestId`, `channel` (`A`–`F`) | shares 16 controls | Synchronizes the recorder. Recorder stage only. Wrong channel costs a penalty. |
| `control_environment` | `roomId`, `requestId`, `system` (`LIGHTING` `VENTILATION`), `setting` | shares 16 controls | Any stage after power. LIGHTING: `EMERGENCY` `WORK` `ULTRAVIOLET`. VENTILATION: `INTAKE` `EXHAUST` `HOLD`; an unsafe valid setting costs a penalty. |
| `arm_exit` | `roomId`, `requestId`, `channel` (`A`–`F`) | 12 | Exit stage only, not while paused. Correct channel arms for 20 s. Wrong channel costs a penalty and disarms. |

`observe` data:

```text
status: roomId, revision, mode, stage (POWER|CABINET|STORY|EXIT), objective, secondsLeft,
        outcome (IN_PROGRESS|ESCAPED|FAILED|INTERRUPTED), paused, practice, armSeconds,
        mistakes, hintsUsed, discoveries,
        systems { power, decoderShift, recorderChannel, lighting, ventilation }
brief: the companion role description
reported: [{ compartment, text }]   clues the human has shared
notes: [{ role (YOU|COMPANION), text }]   the last 80 messages
availableActions: MESSAGE, ARCHIVE and, by stage, CALCULATE and ROUTE_POWER (POWER),
        TUNE_DECODER (CABINET), SYNC_RECORDER (STORY), ARM_EXIT (EXIT),
        CONTROL_ENVIRONMENT (after POWER)
queriesLeft, messagesLeft, calculationsLeft, routesLeft, controlsLeft, armsLeft
```

Error codes and categories:

| Category | Codes | Meaning |
| --- | --- | --- |
| SESSION | `NO_ROOM`, `STALE_ROOM`, `ROOM_CLOSED`, `UNAVAILABLE` | No room, an old `roomId` (observe again), the room has ended, or the plugin is disabled. |
| PREREQUISITE | `WRONG_STAGE`, `NO_POWER`, `REMOTE_POWER_REQUIRED`, `REMOTE_DECODER_REQUIRED`, `REMOTE_RECORDER_REQUIRED` | The action belongs to another stage or needs an earlier step. |
| LIMIT | `RATE_LIMIT`, `BUDGET_EXHAUSTED` | Observe called within one second, or a per-room budget is used up. |
| INPUT | `INVALID_ARGUMENTS`, `UNKNOWN_TOOL`, `INVALID_QUERY`, `INVALID_TEXT`, `INVALID_REQUEST_ID`, `REQUEST_CONFLICT`, `INVALID_NUMBER`, `DIVIDE_BY_ZERO`, `INVALID_OPERATION`, `INVALID_MODE`, `INVALID_SHIFT`, `INVALID_CHANNEL`, `INVALID_SYSTEM`, `INVALID_SETTING`, `PAUSED` | Correct the arguments or wait. |

`retryable` is false for `ROOM_CLOSED`, `UNAVAILABLE` and `BUDGET_EXHAUSTED`, and true otherwise.

There are no tools for inspecting objects, sharing clues, operating breakers, entering the word, arranging strips, requesting hints, pausing, resetting or turning the handle. Those actions exist only in the human interface.

## Built-in companion behaviour

When **Use my BOSS model** is on, BLACKOUT runs the companion through the BOSS AI gateway using the same nine tool definitions.

- A turn starts when a room is in progress, not paused, no turn is running, and either the room is new or the human has done something since the last turn (shared a clue, sent a message, submitted an answer, opened the door, used a hint, paused or resumed).
- Each turn uses temperature 0.2, up to 8 tool steps, a 90-second timeout and a 20,000-token budget.
- The first turn reads the full archive. Archive text (up to 12,000 characters) is kept for later turns.
- The prompt names the current stage's steps, and requires every conclusion to be sent with the message tool, since plain model text is not shown in the room.
- If a turn ends without a message, one short correction turn (3 steps, 4,000 tokens) asks the model to send one.
- A `RATE_LIMIT` on observe is retried once after 1.1 seconds.
- The companion panel shows the model's status, tool calls and tokens. **NUDGE** clears the turn state so the next human action starts a fresh turn.
- The model can never perform human actions. There is no scripted fallback: without a model or agent, the remote systems do nothing.

## Content reference

This section contains puzzle content.

| Incident | People | Safe ventilation | Word pool |
| --- | --- | --- | --- |
| COOLANT | Mara, Ivo | EXHAUST | LIGHT, HAVEN, ALIVE |
| FLOOD | Sena, Orin | INTAKE | SHORE, ABOVE, DRY |
| SPORE | Tali, Ren | HOLD | CLEAN, BLOOM, BREATH |

- Symbols: SUN, WAVE, LEAF, MOON, EYE, STAR. Three are fitted per room; each has a priority from 1 to 6 in the power manual.
- Current values: 4, 5, 8, 10, 14 or 16 A. Supply: 24 or 48 V. Load shown = amps × volts.
- Cipher shift: 1–5. The label is the word shifted forward.
- Waveform and door seal: one symbol each, mapped to channels A–F by separate tables in the recorder index and exit manual.
- Strips: each incident has an alarm line, a shelter line and a rescue line, shuffled and labelled A, B, C. The correct order is alarm, shelter, rescue.
- Each incident has its own ending text, two discoveries, a five-line escape epilogue and a five-line trapped epilogue.

## Development

| Command | Purpose |
| --- | --- |
| `.\gradlew.bat test` | Run the test suite. |
| `.\gradlew.bat buildPluginJar` | Build `build/libs/boss-plugin-blackout-0.5.0.jar`. |
| `.\gradlew.bat build` | Compile, test and build the JAR. |
| `.\gradlew.bat runPrototype` | Open the game in a standalone window without BOSS (no AI gateway; an external agent cannot connect). |
| `.\gradlew.bat smokeUi` | Open the standalone window, start a seeded room, and close. `-PsmokeSeconds=N` sets the duration; `-PcapturePath=file.png` saves a screen capture of the window area, so keep it in front. |
| `.\gradlew.bat renderUi` | Render every screen offscreen (title, four stages, compact layout, escape story, credits, trapped sequence, results) to `build/ui-snapshots/`. No window opens. |
| `.\gradlew.bat installPlugin` | Copy the JAR to `~/.boss_debug/plugins` or `-PbossPluginDir=<dir>`. Close BOSS first. Installing through Toolbox also registers the plugin. |

The renderer (`Snapshot.kt`) and its scripted stand-in companion are excluded from the plugin JAR.

## Project layout

```text
src/main/kotlin/ai/rever/boss/plugin/dynamic/blackout/
  BlackoutPlugin.kt          Registration: MCP tool provider, built-in companion, BLACKOUT tab, room clock tick.
  Prototype.kt               Standalone window and smokeUi harness.
  Snapshot.kt                Offscreen renderer for renderUi (not shipped).
  engine/EscapeRoom.kt       Incidents and seeded room generation. Holds the solution.
  application/Escape.kt      The authoritative room: stages, rules, timers, penalties, views, debrief, activity timeline.
  application/Session.kt     The single room for the plugin and its companion.
  application/Companion.kt   Built-in companion through the BOSS AI gateway.
  mcp/EscapeTools.kt         The nine blackout_v3 tools, validation and error envelope.
  ui/EscapeBoard.kt          Screen state and layout.
  ui/RoomScene.kt, SceneKit.kt  The illustrated room, effects and ending animation.
  ui/Devices.kt              Breaker, cabinet, recorder and door devices.
  ui/CompanionPanel.kt       Remote systems, conversation and activity cards.
  ui/Hud.kt, Opening.kt, Ending.kt, Overlays.kt  Top bar, title screen, ending sequence, dialogs and log.
  ui/Theme.kt, Glyphs.kt, Clues.kt, Effects.kt   Styling, icons, clue parsing, sound.
src/test/kotlin/.../EscapeTest.kt   Test suite.
docs/                        ESCAPE-ROOM.md (rules), AGENT-QUICKSTART.md, ARCHITECTURE.md, VALIDATION.md.
HANDOFF.md, AGENTS.md        Notes for contributors and coding agents.
```

## Tests

`EscapeTest` has 17 tests. They cover:

- 100 seeded rooms escaped using only what each role can legally see, across all incidents.
- Every puzzle requiring a companion action.
- Clues reaching the companion only when shared.
- Standard and Showcase timing rules.
- Recoverable penalties for wrong remote actions.
- Discoveries from environment controls without skipping stages.
- Prerequisites that cannot be skipped.
- Invalid input costing no time while wrong answers do.
- Timeout and frozen terminal state.
- Release expiry and re-arming.
- Pause preserving both timers.
- Request ID idempotency and conflicts.
- v3 tool schemas.
- Rejection of malformed selectors and stale rooms.
- Calculator bounds.
- Debriefs excluding conversation.
- The companion activity timeline, which never shows manual contents.

## Compatibility

- Plugin API 1.0.89, Kotlin 2.3.0, Compose Multiplatform 1.10.0, JDK 17.
- Tested on Windows 11 with a BOSS 9.5.12 development build, with an external agent in the companion seat over MCP.

## Access, privacy and data

- BLACKOUT declares no permissions and makes no network calls of its own. The built-in companion uses BOSS's configured AI gateway. External agents connect through BOSS's local MCP server.
- The companion never receives the room's solution or physical state, only clues the human shares, its manuals and the public room status.
- The human never sees the manuals. Activity cards show archive record names, never their contents.
- Saved debriefs contain events and counts only. Sound cues are generated locally; no media files are shipped.
