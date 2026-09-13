package ai.rever.boss.plugin.dynamic.blackout.mcp

import ai.rever.boss.plugin.api.*
import ai.rever.boss.plugin.dynamic.blackout.application.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

/** Remote-system tools only. Human physical actions intentionally have no definitions. */
class EscapeTools(private val session: Session) {
    fun tools(): List<McpToolDefinition> = listOf(
        definition("observe", "Read room status, available remote actions, messages and physical clues explicitly shared by the human. Once per second.", emptyMap()),
        definition("archive", "Read manuals and incident records. Query ALL, POWER, CABINET, RECORDER, EXIT, ENVIRONMENT or an incident term. Twelve queries per room.", mapOf("roomId" to textSchema(64), "query" to textSchema(120))),
        definition("message", "Speak to the human through the room terminal. Give one concise next step. Thirty messages per room.", mapOf("roomId" to textSchema(64), "requestId" to textSchema(64), "text" to textSchema(500))),
        definition("calculate", "Use the bounded emergency calculator. Operations are ADD, SUBTRACT, MULTIPLY or DIVIDE on two integers.", mapOf("roomId" to textSchema(64), "requestId" to textSchema(64), "operation" to choices(Arithmetic.entries.map { it.name }), "a" to integerSchema(), "b" to integerSchema())),
        definition("route_power", "Route LOW, NORMAL or HIGH power after calculating current from the human's readings. The human still operates the breakers.", mapOf("roomId" to textSchema(64), "requestId" to textSchema(64), "mode" to choices(PowerMode.entries.map { it.name }))),
        definition("tune_decoder", "Physically tune the cabinet decoder to offset 1–5 using the cabinet manual. A wrong valid offset costs time but can be corrected.", mapOf("roomId" to textSchema(64), "requestId" to textSchema(64), "shift" to rangeSchema(1, 5))),
        definition("sync_recorder", "Synchronize recorder channel A–F from the human's waveform and the recorder index. The human still orders the strips.", mapOf("roomId" to textSchema(64), "requestId" to textSchema(64), "channel" to choices(CHANNELS))),
        definition("control_environment", "Control optional room systems after power is restored. LIGHTING uses EMERGENCY, WORK or ULTRAVIOLET; VENTILATION uses INTAKE, EXHAUST or HOLD.", mapOf("roomId" to textSchema(64), "requestId" to textSchema(64), "system" to choices(listOf("LIGHTING", "VENTILATION")), "setting" to choices(listOf("EMERGENCY", "WORK", "ULTRAVIOLET", "INTAKE", "EXHAUST", "HOLD")))),
        definition("arm_exit", "Authorize exit channel A–F from the shared door seal and exit manual. The human must turn the handle within 20 seconds.", mapOf("roomId" to textSchema(64), "requestId" to textSchema(64), "channel" to choices(CHANNELS)))
    )

    fun aiTools() = tools().map { AiToolSpec(it.name, it.description, it.inputSchema) }

    private fun definition(name: String, description: String, properties: Map<String, JsonElement>) = McpToolDefinition(
        name = PREFIX + name,
        description = description,
        inputSchema = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties))
            put("required", JsonArray(properties.keys.map { JsonPrimitive(it) }))
            put("additionalProperties", false)
        }.toString(),
        readOnly = name == "observe",
        handler = McpToolHandler { args -> currentCoroutineContext().ensureActive(); call(name, args.raw) }
    )

    fun call(name: String, raw: String): McpToolResult = try {
        if (raw.length > 4096) throw GameError("INVALID_ARGUMENTS", "Arguments exceed 4096 characters.")
        val obj = try { Json.parseToJsonElement(raw) as? JsonObject } catch (_: IllegalArgumentException) { null }
            ?: throw GameError("INVALID_ARGUMENTS", "Expected a JSON object.")
        val expected = when (name) {
            "observe" -> emptySet()
            "archive" -> setOf("roomId", "query")
            "message" -> setOf("roomId", "requestId", "text")
            "calculate" -> setOf("roomId", "requestId", "operation", "a", "b")
            "route_power" -> setOf("roomId", "requestId", "mode")
            "tune_decoder" -> setOf("roomId", "requestId", "shift")
            "sync_recorder" -> setOf("roomId", "requestId", "channel")
            "control_environment" -> setOf("roomId", "requestId", "system", "setting")
            "arm_exit" -> setOf("roomId", "requestId", "channel")
            else -> throw GameError("UNKNOWN_TOOL", "Unknown remote tool; human actions are unavailable to the companion.")
        }
        if (obj.keys != expected) throw GameError("INVALID_ARGUMENTS", "Provide exactly ${expected.joinToString()}. No role or seat selectors.")
        val game = session.currentEscape()
        if (name != "observe" && string(obj, "roomId") != game.id) throw GameError("STALE_ROOM", "Observe the current room first.")
        val payload = when (name) {
            "observe" -> Json.encodeToJsonElement(game.observe())
            "archive" -> Json.encodeToJsonElement(game.archive(string(obj, "query")))
            "message" -> Json.encodeToJsonElement(game.message(string(obj, "requestId"), string(obj, "text")))
            "calculate" -> Json.encodeToJsonElement(game.calculate(string(obj, "requestId"), enumValue<Arithmetic>(obj, "operation", "INVALID_OPERATION"), integer(obj, "a"), integer(obj, "b")))
            "route_power" -> Json.encodeToJsonElement(game.routePower(string(obj, "requestId"), enumValue<PowerMode>(obj, "mode", "INVALID_MODE")))
            "tune_decoder" -> Json.encodeToJsonElement(game.tuneDecoder(string(obj, "requestId"), integer(obj, "shift")))
            "sync_recorder" -> Json.encodeToJsonElement(game.syncRecorder(string(obj, "requestId"), string(obj, "channel")))
            "control_environment" -> Json.encodeToJsonElement(game.controlEnvironment(string(obj, "requestId"), string(obj, "system"), string(obj, "setting")))
            else -> Json.encodeToJsonElement(game.armExit(string(obj, "requestId"), string(obj, "channel")))
        }
        McpToolResult(buildJsonObject { put("schemaVersion", 3); put("ok", true); put("data", payload) }.toString())
    } catch (e: GameError) {
        McpToolResult(buildJsonObject {
            put("schemaVersion", 3); put("ok", false); put("code", e.code); put("message", e.message)
            put("category", category(e.code)); put("retryable", e.code !in setOf("ROOM_CLOSED", "UNAVAILABLE", "BUDGET_EXHAUSTED"))
        }.toString(), true)
    }

    private inline fun <reified T : Enum<T>> enumValue(obj: JsonObject, key: String, code: String): T =
        enumValues<T>().find { it.name == string(obj, key) } ?: throw GameError(code, "Invalid $key value.")

    private fun string(obj: JsonObject, key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw GameError("INVALID_ARGUMENTS", "$key must be a string.")

    private fun integer(obj: JsonObject, key: String) = (obj[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        ?: throw GameError("INVALID_ARGUMENTS", "$key must be an integer.")

    private fun category(code: String) = when (code) {
        "STALE_ROOM", "NO_ROOM", "ROOM_CLOSED", "UNAVAILABLE" -> "SESSION"
        "WRONG_STAGE", "NO_POWER", "REMOTE_POWER_REQUIRED", "REMOTE_DECODER_REQUIRED", "REMOTE_RECORDER_REQUIRED" -> "PREREQUISITE"
        "RATE_LIMIT", "BUDGET_EXHAUSTED" -> "LIMIT"
        else -> "INPUT"
    }

    companion object {
        const val PREFIX = "blackout_v3_"
        private val CHANNELS = listOf("A", "B", "C", "D", "E", "F")
        private fun textSchema(max: Int) = buildJsonObject { put("type", "string"); put("minLength", 1); put("maxLength", max) }
        private fun choices(values: List<String>) = buildJsonObject { put("type", "string"); put("enum", JsonArray(values.map { JsonPrimitive(it) })) }
        private fun integerSchema() = rangeSchema(-1_000_000, 1_000_000)
        private fun rangeSchema(min: Int, max: Int) = buildJsonObject { put("type", "integer"); put("minimum", min); put("maximum", max) }
    }
}
