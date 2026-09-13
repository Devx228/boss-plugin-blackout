# Companion quickstart

This is the escape-room interface, version 2. The old investigation v1 tools are not
registered by the current plugin.

## In BOSS

Open BLACKOUT and choose Connect. Enable the configured BOSS model, or leave that off
and connect one external MCP agent. The built-in companion uses the same six schemas.
Do not enable both at once: BOSS does not distinguish separate agents on this seat.

An external agent can start with:

> Play my BLACKOUT companion. Call blackout_v2_observe and read blackout_v2_archive with
> ALL. You have manuals; I have the physical clues. Ask me to share what I find. Configure
> remote power, help decode the password and reconstruct the story, then authorize the
> correct exit channel. Send your suggestions with blackout_v2_message.

## Sequence

1. `blackout_v2_observe {}` — take `data.status.roomId` and the objective.
2. `blackout_v2_archive {"roomId":"…","query":"ALL"}` — retain the records.
3. Ask the human to share the breaker clue. The manual has all six symbols but cannot
   identify the fitted three. Sort only the human's three by priority.
4. Use `blackout_v2_calculate` with operation DIVIDE, reported load watts as `a`, and
   supply volts as `b` (plus roomId and requestId). Use the resulting current to select
   LOW (1–6 A), NORMAL (7–12 A), HIGH (13–18 A).
   Call `blackout_v2_route {"roomId":"…","requestId":"power1","mode":"NORMAL"}`
   with the mode you actually derived. The tool configures power; it does not operate the
   human's breakers or prove that the setting is right.
5. Send the startup sequence through message. Wait for the human to operate it.
6. Ask for the cabinet label; shift its letters backward by the manual's offset. Give the
   decoded word with brief reasoning. The human enters it.
7. Ask for the memory strips. Reconstruct safety shutdown, shelter, rescue in that order.
   Tell the human the local strip labels, not just the generic events.
8. At EXIT, ask for the door routing seal. Look up its channel in the exit manual, then
   call `blackout_v2_arm {"roomId":"…","requestId":"release1","channel":"C"}`
   with the channel you derived. Tell the human to turn the handle now.

The example values above are schema illustrations, not solutions for your room.

## Budgets and retries

- Observe at most once per second; 12 archive queries, 16 calculations, 30 messages, eight power settings,
  and 12 release authorizations per room.
- Messages are 1–500 characters. Request IDs are 1–64 letters, digits, `_` or `-`.
- Reuse the same request ID and exact payload to retry an accepted mutation safely.
- To deliberately re-arm after the window expires, use a new request ID. Replaying an old
  receipt does not extend the window.
- Every call except observe carries the current room ID. STALE_ROOM means observe again.
- INVALID_ARGUMENTS means correct the schema, not keep resending the same invalid call.
- ROOM_CLOSED means stop. Never claim the human escaped unless the tool status says ESCAPED.

There are deliberately no agent tools for inspecting the room, entering passwords,
arranging strips, hints, pause, reset, or turning the handle. No user/model free-text is
saved in the debrief. The local filesystem and screen are not protected against other
tools; this is a trusted cooperative game, not competitive anti-cheat infrastructure.
