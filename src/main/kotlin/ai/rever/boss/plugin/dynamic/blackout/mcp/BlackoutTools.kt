package ai.rever.boss.plugin.dynamic.blackout.mcp

import ai.rever.boss.plugin.api.*
import ai.rever.boss.plugin.dynamic.blackout.application.*
import ai.rever.boss.plugin.dynamic.blackout.engine.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class BlackoutTools(private val session: Session) : McpToolProvider {
    override val providerId = "ai.rever.boss.plugin.dynamic.blackout"
    private val json = Json
    override fun tools(): List<McpToolDefinition> = listOf(
        definition("observe", "Read BLUE engineer's view and manual. Once per second. No route map or rival secrets.", emptyMap()),
        definition("scan", "Use the one diagnostic scan; returns polarity. Retry with the same requestId.", common),
        definition("message", "Send a plain-text message to your pilot. Six per round, 500 characters each.", common + ("text" to stringSchema(500))),
        definition("commit", "Lock your engineering configuration. Human chooses the combat action and route.", common + mapOf(
            "thermal" to enumSchema(Thermal.entries.map { it.name }),
            "polarity" to enumSchema(Polarity.entries.map { it.name }),
            "output" to enumSchema(Output.entries.map { it.name })))
    )

    /**
     * The same four tools, described for the host AI gateway. Built from [tools] so the
     * in-app engineer seat and an external MCP agent can never drift apart.
     */
    fun aiTools(): List<AiToolSpec> = tools().map { AiToolSpec(it.name, it.description, it.inputSchema) }

    private fun definition(name: String, description: String, properties: Map<String, JsonElement>) = McpToolDefinition(
        name = PREFIX + name, description = description,
        inputSchema = buildJsonObject {
            put("type", "object"); put("additionalProperties", false)
            put("properties", JsonObject(properties)); put("required", JsonArray(properties.keys.map { JsonPrimitive(it) }))
        }.toString(), readOnly = name == "observe",
        handler = McpToolHandler { args -> currentCoroutineContext().ensureActive(); call(name, args.raw) }
    )

    fun call(name: String, raw: String): McpToolResult {
        try {
            if (raw.length > 4096) throw GameError("INVALID_ARGUMENTS", "Arguments exceed 4096 characters.")
            val obj = try { json.parseToJsonElement(raw) as? JsonObject } catch (_: IllegalArgumentException) { null }
                ?: throw GameError("INVALID_ARGUMENTS", "Expected a JSON object.")
            val keys = when (name) {
                "observe" -> emptySet()
                "scan" -> common.keys
                "message" -> common.keys + "text"
                "commit" -> common.keys + setOf("thermal", "polarity", "output")
                else -> throw GameError("UNKNOWN_TOOL", "Unknown game tool.")
            }
            if (obj.keys != keys) throw GameError("INVALID_ARGUMENTS", "Provide exactly: ${keys.joinToString()}. Team, role and administrative selectors are forbidden.")
            val match = session.current()
            val payload = if (name == "observe") json.encodeToJsonElement(match.observe()) else {
                if (string(obj, "matchId") != match.id) throw GameError("STALE_MATCH", "Observe the new match before issuing commands.")
                val value = obj["round"] as? JsonPrimitive
                val round = value?.takeUnless { it.isString }?.intOrNull
                    ?: throw GameError("INVALID_ARGUMENTS", "round must be an integer.")
                val requestId = string(obj, "requestId")
                val ack = when (name) {
                    "scan" -> match.scan(round, requestId)
                    "message" -> match.message(round, requestId, string(obj, "text"))
                    else -> match.commitAgent(round, requestId, AgentOrder(enumValue<Thermal>(obj, "thermal"),
                        enumValue<Polarity>(obj, "polarity"), enumValue<Output>(obj, "output")))
                }
                json.encodeToJsonElement(ack)
            }
            return McpToolResult(buildJsonObject { put("schemaVersion", 1); put("ok", true); put("data", payload) }.toString())
        } catch (e: GameError) {
            return McpToolResult(buildJsonObject {
                put("schemaVersion", 1); put("ok", false); put("code", e.code); put("message", e.message)
            }.toString(), isError = true)
        }
    }
    private fun string(obj: JsonObject, key: String): String = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw GameError("INVALID_ARGUMENTS", "$key must be a string.")
    private inline fun <reified T : Enum<T>> enumValue(obj: JsonObject, key: String): T =
        enumValues<T>().find { it.name == string(obj, key) } ?: throw GameError("INVALID_ARGUMENTS", "Invalid $key; use ${enumValues<T>().joinToString { it.name }}.")
    companion object {
        const val PREFIX = "blackout_v1_"
        private fun stringSchema(max: Int) = buildJsonObject { put("type", "string"); put("minLength", 1); put("maxLength", max) }
        private fun enumSchema(values: List<String>) = buildJsonObject { put("type", "string"); put("enum", JsonArray(values.map { JsonPrimitive(it) })) }
        private val common = mapOf("matchId" to stringSchema(64), "round" to buildJsonObject { put("type", "integer"); put("minimum", 1); put("maximum", 6) }, "requestId" to stringSchema(64))
    }
}
