package ai.rever.boss.plugin.dynamic.blackout.mcp

import ai.rever.boss.plugin.api.AiToolSpec
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult
import ai.rever.boss.plugin.dynamic.blackout.application.GameError
import ai.rever.boss.plugin.dynamic.blackout.application.Session
import ai.rever.boss.plugin.dynamic.blackout.application.Verdict
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * The archivist's seat. Four tools, and not one of them can see the station: the agent
 * reads records and talks to the pilot, and the pilot is the only one who can look at a
 * compartment. Naming the culprit is deliberately not a tool. The human makes that call.
 */
class BlackoutTools(private val session: Session) : McpToolProvider {

    override val providerId = "ai.rever.boss.plugin.dynamic.blackout"

    private val json = Json

    override fun tools(): List<McpToolDefinition> = listOf(
        definition(
            "observe",
            "Read the case brief, the clock, every finding your pilot has reported and your remaining budgets. " +
                "Once per second. Carries no physical state your pilot has not sent you.",
            emptyMap()
        ),
        definition(
            "archive",
            "Search the station archive: roster, signed work orders, telemetry, comms and supply. " +
                "Pass ALL for the whole archive. Ten searches per case.",
            mapOf("query" to stringSchema(120))
        ),
        definition(
            "message",
            "Send one plain-text line to your pilot. Fourteen per case, 500 characters each.",
            mapOf("requestId" to stringSchema(64), "text" to stringSchema(500))
        ),
        definition(
            "mark",
            "Draw your verdict on a compartment onto the pilot's map, with a one-line reason. " +
                "Use SUSPECT for somewhere worth spending a walk on, CLEAR for somewhere ruled out.",
            mapOf(
                "requestId" to stringSchema(64),
                "compartment" to stringSchema(64),
                "verdict" to enumSchema(Verdict.entries.map { it.name }),
                "reason" to stringSchema(200)
            )
        )
    )

    /**
     * The same four tools described for the host AI gateway, built from [tools] so the
     * in-app archivist seat and an external MCP agent can never drift apart.
     */
    fun aiTools(): List<AiToolSpec> = tools().map { AiToolSpec(it.name, it.description, it.inputSchema) }

    private fun definition(name: String, description: String, properties: Map<String, JsonElement>) = McpToolDefinition(
        name = PREFIX + name,
        description = description,
        inputSchema = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
            put("properties", JsonObject(properties))
            put("required", JsonArray(properties.keys.map { JsonPrimitive(it) }))
        }.toString(),
        readOnly = name == "observe" || name == "archive",
        handler = McpToolHandler { args -> currentCoroutineContext().ensureActive(); call(name, args.raw) }
    )

    fun call(name: String, raw: String): McpToolResult {
        try {
            if (raw.length > 4096) throw GameError("INVALID_ARGUMENTS", "Arguments exceed 4096 characters.")
            val obj = try {
                json.parseToJsonElement(raw) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            } ?: throw GameError("INVALID_ARGUMENTS", "Expected a JSON object.")

            val expected = when (name) {
                "observe" -> emptySet()
                "archive" -> setOf("query")
                "message" -> setOf("requestId", "text")
                "mark" -> setOf("requestId", "compartment", "verdict", "reason")
                else -> throw GameError("UNKNOWN_TOOL", "Unknown game tool.")
            }
            if (obj.keys != expected) throw GameError(
                "INVALID_ARGUMENTS",
                "Provide exactly: " + expected.joinToString() + ". Seat, role and administrative selectors are forbidden."
            )

            val game = session.current()
            val payload: JsonElement = when (name) {
                "observe" -> json.encodeToJsonElement(game.observe())
                "archive" -> json.encodeToJsonElement(game.archive(string(obj, "query")))
                "message" -> json.encodeToJsonElement(game.message(string(obj, "requestId"), string(obj, "text")))
                else -> json.encodeToJsonElement(
                    game.mark(
                        string(obj, "requestId"),
                        string(obj, "compartment"),
                        enumValue<Verdict>(obj, "verdict"),
                        string(obj, "reason")
                    )
                )
            }
            return McpToolResult(
                buildJsonObject {
                    put("schemaVersion", 1); put("ok", true); put("data", payload)
                }.toString()
            )
        } catch (e: GameError) {
            return McpToolResult(
                buildJsonObject {
                    put("schemaVersion", 1); put("ok", false); put("code", e.code); put("message", e.message)
                }.toString(),
                isError = true
            )
        }
    }

    private fun string(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw GameError("INVALID_ARGUMENTS", key + " must be a string.")

    private inline fun <reified T : Enum<T>> enumValue(obj: JsonObject, key: String): T =
        enumValues<T>().find { it.name == string(obj, key) }
            ?: throw GameError("INVALID_ARGUMENTS", "Invalid " + key + "; use " + enumValues<T>().joinToString { it.name } + ".")

    companion object {
        const val PREFIX = "blackout_v1_"

        private fun stringSchema(max: Int) = buildJsonObject {
            put("type", "string"); put("minLength", 1); put("maxLength", max)
        }

        private fun enumSchema(values: List<String>) = buildJsonObject {
            put("type", "string"); put("enum", JsonArray(values.map { JsonPrimitive(it) }))
        }
    }
}
