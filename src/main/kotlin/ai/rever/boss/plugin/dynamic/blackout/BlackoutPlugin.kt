package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.api.*
import ai.rever.boss.plugin.dynamic.blackout.application.Companion
import ai.rever.boss.plugin.dynamic.blackout.application.Session
import ai.rever.boss.plugin.dynamic.blackout.ui.EscapeBoard
import ai.rever.boss.plugin.dynamic.blackout.mcp.EscapeTools
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.*

const val PLUGIN_ID = "ai.rever.boss.plugin.dynamic.blackout"

object BlackoutTab : TabTypeInfo {
    override val typeId = TabTypeId("blackout", PLUGIN_ID)
    override val displayName = "BLACKOUT"
    override val icon = Icons.Default.FlashOn

    /**
     * Without this the tab type registers but never appears in the new tab dialog: the
     * host lists only plugin types whose newTabSpec is non-null, and the default is null.
     *
     * A blank label, a blank placeholder and an optional input together mean "no input
     * step" to the host, so picking BLACKOUT opens the board straight away. The game asks
     * for nothing up front; the briefing screen is where a duel gets configured.
     */
    override val newTabSpec = NewTabSpec(
        order = 50,
        inputLabel = "",
        inputPlaceholder = "",
        inputOptional = true,
        confirmLabel = "Play"
    )

    override fun createTabInfo(input: String, context: NewTabContext): TabInfo = BlackoutInfo(input)
}

data class BlackoutInfo(override val id: String) : TabInfo {
    override val typeId = BlackoutTab.typeId
    override val title = "BLACKOUT"
    override val icon = Icons.Default.FlashOn
}

class BlackoutPlugin : DynamicPlugin {
    override val pluginId = PLUGIN_ID
    override val displayName = "BLACKOUT"
    override val version = "0.4.0"
    override val description = "A human and an AI companion solve a sealed room together before the air runs out"
    override val author = "BLACKOUT contributors"

    private var context: PluginContext? = null
    private var session = Session()
    private var job: Job? = null

    override fun register(context: PluginContext) {
        this.context = context
        session = Session()
        val storage = context.pluginStorageFactory?.createStorage(PLUGIN_ID)
        val tools = EscapeTools(session)
        context.registerMcpToolProvider(object : McpToolProvider {
            override val providerId = PLUGIN_ID
            override fun tools() = tools.tools()
        })

        // The host gateway can hold the archivist's seat through exactly these tools.
        // When the host exposes none, the seat stays open for an external MCP agent.
        val gateway = runCatching { context.getPluginAPI(AiGatewayAPI::class.java) }.getOrNull()
        val partner = Companion(gateway, tools, session)
        session.partner = partner
        partner.start(context.pluginScope)

        context.tabRegistry.registerTabType(BlackoutTab) { info, componentContext ->
            object : TabComponentWithUI, ComponentContext by componentContext {
                override val tabTypeInfo = BlackoutTab
                override val config = info
                @Composable override fun Content() { EscapeBoard(session, storage) }
            }
        }
        job = context.pluginScope.launch {
            while (isActive) { session.escape?.tick(); delay(250) }
        }
    }

    override fun dispose() {
        session.dispose()
        job?.cancel(); job = null
        context?.unregisterMcpToolProvider(PLUGIN_ID)
        context?.tabRegistry?.unregisterTabType(BlackoutTab.typeId)
        context = null
    }
}
