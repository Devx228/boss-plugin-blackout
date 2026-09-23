# Companion quickstart

BLACKOUT v3 gives the AI the room's remote-systems seat. Use one built-in BOSS model or one external MCP agent, never both; the shared local endpoint does not identify separate callers.

Suggested external prompt:

> Play my BLACKOUT remote-systems companion. Observe the room, read the ALL archive, ask me to share physical clues, operate each required remote system, and send every next step through the message tool. Never claim you can see or touch the room.

## Sequence

1. Call `blackout_v3_observe {}` and retain the current `roomId`.
2. Call `blackout_v3_archive` with query `ALL` and retain the records.
3. Ask for the breaker panel. Calculate amps from watts divided by volts, call `blackout_v3_route_power`, and message the fitted symbols in ascending manual priority.
4. Ask for the powered cabinet. Look up the serial plate prefix the human shares in the cabinet manual, call `blackout_v3_tune_decoder` with that offset, decode the label backward, and message the word.
5. Ask for the recorder waveform and strips. Map the waveform to a channel, call `blackout_v3_sync_recorder`, and message the cause-to-effect strip order.
6. Ask for the exit seal. Map it to a channel, call `blackout_v3_arm_exit`, and immediately message the human to turn the handle.
7. After power restoration, `blackout_v3_control_environment` may set LIGHTING to EMERGENCY, WORK or ULTRAVIOLET and VENTILATION to INTAKE, EXHAUST or HOLD. These controls reveal optional story details. The safe ventilation mode also recovers air once, and ultraviolet makes the human's next hint free; an unsafe valid ventilation choice costs time and can be corrected.

All mutating calls require a unique request ID. Replaying an accepted request with the identical payload returns the original receipt; using the same ID for different content fails. Every call except observe requires the current room ID. Observe is limited to once per second.

Errors include a category and retryable flag. `STALE_ROOM` means observe again, `WRONG_STAGE` means wait for the human, `CLUE_NOT_SHARED` means ask the human to share that object, `INVALID_ARGUMENTS` means correct the schema, and `ROOM_CLOSED` means stop.

The agent cannot inspect physical objects, share clues for the human, operate breakers, enter the password, arrange strips, request hints, pause, reset or turn the handle. Free text and model reasoning are excluded from saved debriefs.
