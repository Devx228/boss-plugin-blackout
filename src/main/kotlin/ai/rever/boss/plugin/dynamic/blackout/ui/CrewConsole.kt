package ai.rever.boss.plugin.dynamic.blackout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.boss.plugin.dynamic.blackout.application.CrewEvent
import ai.rever.boss.plugin.dynamic.blackout.application.CrewMessage
import ai.rever.boss.plugin.dynamic.blackout.application.CrewStatus

/**
 * The engineer's seat. It shows which model is holding it and every tool call it makes,
 * so a spectator can tell a live agent from a script without taking anyone's word for it.
 */
@Composable
fun EngineerConsole(
    status: CrewStatus,
    locked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Panel(title = "ENGINEER SEAT", modifier = modifier, accent = Ink.Violet, trailing = {
        Pill(if (locked) "ORDER LOCKED" else "OPEN", if (locked) Ink.Green else Ink.Amber, filled = locked)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Body(status.label, if (status.available) Ink.Text else Ink.Dim)
                Note(status.status)
            }
            if (status.available) {
                Switch(
                    checked = status.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Ink.Violet, checkedTrackColor = Ink.Violet)
                )
            }
        }
        if (!status.available) {
            Note("Attach any MCP agent to the four blackout_v1 tools instead. The seat is the same either way.")
        } else if (!status.enabled) {
            Note("Switch on to let the host model take the engineer's seat, or leave it off and drive the seat from your own MCP agent.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("ROUND ${status.servedRound}", Ink.Dim)
            Pill("${status.toolCalls} TOOL CALLS", Ink.Dim)
            if (status.tokens > 0) Pill("${status.tokens} TOKENS", Ink.Dim)
            if (status.busy) Pill("THINKING", Ink.Cyan, filled = true)
        }
        if (status.log.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState())
                    .background(Ink.Void, RoundedCornerShape(6.dp)).border(1.dp, Ink.Edge, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                status.log.takeLast(40).forEach { event ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            marker(event.kind),
                            color = colourOf(event.kind),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(18.dp)
                        )
                        Text(event.text, color = colourOf(event.kind), fontSize = 10.5.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

private fun marker(kind: CrewEvent.Kind) = when (kind) {
    CrewEvent.Kind.CALL -> ">>"
    CrewEvent.Kind.RESULT -> "<<"
    CrewEvent.Kind.SAY -> "**"
    CrewEvent.Kind.FAIL -> "!!"
    CrewEvent.Kind.NOTE -> "--"
}

private fun colourOf(kind: CrewEvent.Kind) = when (kind) {
    CrewEvent.Kind.CALL -> Ink.Cyan
    CrewEvent.Kind.RESULT -> Ink.Dim
    CrewEvent.Kind.SAY -> Ink.Violet
    CrewEvent.Kind.FAIL -> Ink.Red
    CrewEvent.Kind.NOTE -> Ink.Faint
}

/** The only channel between the two halves of the crew. */
@Composable
fun CrewComms(
    messages: List<CrewMessage>,
    remaining: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    Panel(title = "CREW CHANNEL", modifier = modifier, accent = Ink.Cyan, trailing = {
        Note("$remaining PILOT MESSAGES LEFT")
    }) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 190.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) {
                Note("Nothing said this round. The engineer knows the bus condition; you know the routes. Neither of you wins quietly.")
            }
            messages.forEach { message ->
                val pilot = message.role == "PILOT"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (pilot) "PILOT" else "ENGR",
                        color = if (pilot) Ink.Cyan else Ink.Violet,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(38.dp)
                    )
                    Text(message.text, color = Ink.Text, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= 500) draft = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && remaining > 0,
            textStyle = androidx.compose.ui.text.TextStyle(color = Ink.Text, fontSize = 12.sp),
            label = { Note("Report route properties (${draft.length}/500)") },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Ink.Cyan,
                unfocusedBorderColor = Ink.Edge,
                cursorColor = Ink.Cyan
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSend(draft); draft = "" },
                enabled = enabled && draft.isNotBlank() && remaining > 0,
                colors = ButtonDefaults.buttonColors(backgroundColor = Ink.Cyan, contentColor = Ink.Void)
            ) { Text("SEND", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            listOf("Hot bus?", "Polarity?", "Route is high output").forEach { hint ->
                QuickFill(hint) { draft = hint }
            }
        }
    }
}

@Composable
private fun QuickFill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, Ink.Edge, RoundedCornerShape(4.dp))
            .background(Color.Transparent, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) { Note(text, Ink.Dim) }
}
