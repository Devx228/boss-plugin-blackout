package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.dynamic.blackout.application.EscapeStage
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.OutlinedButton
import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Flat elevation drawn in code. Hit targets are normal keyboard-focusable buttons. */
@Composable fun RoomScene(stage: EscapeStage, selected: String?, escaped: Boolean, enabled: Boolean,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val light by animateColorAsState(if (stage == EscapeStage.POWER) Ink.Faint else Ink.Amber, label = "power restored")
    BoxWithConstraints(modifier.height(290.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            fun box(x: Float, y: Float, width: Float, height: Float, color: Color = Ink.Line, fill: Boolean = false) {
                if (fill) drawRect(color, Offset(w*x,h*y), Size(w*width,h*height))
                else drawRect(color, Offset(w*x,h*y), Size(w*width,h*height), style = Stroke(1.5f))
            }
            // Room perimeter and quiet floor lines.
            box(.025f,.04f,.95f,.89f)
            drawLine(Ink.Line, Offset(w*.025f,h*.79f), Offset(w*.975f,h*.79f), 1f)
            drawLine(Ink.Line, Offset(w*.025f,h*.11f), Offset(w*.975f,h*.11f), 1f)
            for (i in 1..4) drawLine(Ink.Line.copy(alpha=.45f), Offset(w*.03f,h*(.79f+i*.028f)),Offset(w*.97f,h*(.79f+i*.028f)),1f)
            // Overhead light and cable, fed by the breaker.
            box(.37f,.14f,.23f,.025f, light, true)
            drawLine(light.copy(alpha=.35f),Offset(w*.16f,h*.18f),Offset(w*.16f,h*.13f),1.5f)
            drawLine(light.copy(alpha=.35f),Offset(w*.16f,h*.13f),Offset(w*.37f,h*.13f),1.5f)
            // Breaker box.
            box(.08f,.23f,.16f,.25f,if(selected=="panel") Ink.Amber else Ink.Dim)
            for(i in 0..2) {
                box(.10f+i*.045f,.29f,.023f,.075f,Ink.Faint)
                box(.104f+i*.045f,if(stage==EscapeStage.POWER) .34f else .30f,.015f,.015f,light,true)
            }
            // Desk and companion terminal.
            box(.30f,.53f,.19f,.025f,Ink.Dim,true)
            drawLine(Ink.Dim,Offset(w*.32f,h*.56f),Offset(w*.32f,h*.79f),2f)
            drawLine(Ink.Dim,Offset(w*.47f,h*.56f),Offset(w*.47f,h*.79f),2f)
            box(.345f,.36f,.10f,.14f,Ink.Dim)
            box(.356f,.378f,.078f,.095f,Ink.Surface,true)
            drawLine(Ink.Amber,Offset(w*.368f,h*.415f),Offset(w*.405f,h*.415f),2f)
            drawLine(Ink.Faint,Offset(w*.368f,h*.44f),Offset(w*.426f,h*.44f),1f)
            // Cabinet, with a visual seam that opens once decoded.
            box(.54f,.28f,.16f,.51f,if(selected=="cabinet") Ink.Amber else Ink.Dim)
            val open = stage >= EscapeStage.STORY
            drawLine(if(open) Ink.Amber else Ink.Line,Offset(w*.62f,h*.29f),Offset(w*.62f,h*.78f),if(open) 3f else 1f)
            box(.594f,.47f,.018f,.045f,light,true)
            if(open) for(i in 0..2) box(.55f,.35f+i*.105f,.055f,.04f,Ink.Dim)
            // Door and manual-release handle.
            box(.77f,.20f,.17f,.59f,if(selected=="door") Ink.Amber else Ink.Dim)
            if(escaped) box(.785f,.215f,.14f,.56f,Ink.Amber.copy(alpha=.16f),true)
            box(.81f,.26f,.09f,.10f,Ink.Line)
            drawLine(if(stage==EscapeStage.EXIT) Ink.Amber else Ink.Faint,Offset(w*.895f,h*.52f),Offset(w*.925f,h*.52f),3f)
            // Dotted emergency path keeps the exit visually legible.
            drawLine(Ink.Faint,Offset(w*.49f,h*.87f),Offset(w*.85f,h*.87f),1f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(4f,6f)))
        }
        fun position(x: Float, y: Float) = Modifier.offset(maxWidth*x, 290.dp*y)
        RoomTarget("BREAKER", selected=="panel", enabled, position(.055f,.58f)) { onSelect("panel") }
        RoomTarget("CABINET", selected=="cabinet", enabled && stage>=EscapeStage.CABINET, position(.515f,.81f)) { onSelect("cabinet") }
        RoomTarget("DOOR", selected=="door", enabled, position(.755f,.81f)) { onSelect("door") }
        if(stage>=EscapeStage.STORY) RoomTarget("RECORDER", selected=="recorder", enabled, position(.28f,.62f)) { onSelect("recorder") }
    }
}

@Composable private fun RoomTarget(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier=modifier, enabled=enabled,
        contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp),
        colors=ButtonDefaults.outlinedButtonColors(backgroundColor=Ink.Ground,contentColor=if(selected) Ink.Amber else Ink.Text)) {
        Label(label,if(!enabled) Ink.Faint else if(selected) Ink.Amber else Ink.Text)
    }
}
