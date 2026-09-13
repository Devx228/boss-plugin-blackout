package ai.rever.boss.plugin.dynamic.blackout.mcp

import ai.rever.boss.plugin.api.*
import ai.rever.boss.plugin.dynamic.blackout.application.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

/** Human actions intentionally have no tool definitions. Session, not model arguments, binds the seat. */
class EscapeTools(private val session: Session) {
    fun tools(): List<McpToolDefinition> = listOf(
        definition("observe", "Read your companion view, room status and clues explicitly shared by the human. Once per second.", emptyMap()),
        definition("archive", "Read emergency manuals and shift records. Query ALL, POWER, CABINET, RECORDER or EXIT. Twelve queries per room.", mapOf("roomId" to schema(64), "query" to schema(120))),
        definition("message", "Send the human a concise suggestion or question. Thirty messages per room.", mapOf("roomId" to schema(64), "requestId" to schema(64), "text" to schema(500))),
        definition("calculate", "Use the emergency terminal calculator. ADD, SUBTRACT, MULTIPLY or DIVIDE two integers. Sixteen calls per room. No code execution or network access.",
            mapOf("roomId" to schema(64),"requestId" to schema(64),"operation" to choices(Arithmetic.entries.map {it.name}),"a" to numberSchema(),"b" to numberSchema())),
        definition("route", "Configure remote supply LOW, NORMAL or HIGH using the human's reported current draw and your manual. Only the companion can do this. Eight settings per room.", mapOf("roomId" to schema(64), "requestId" to schema(64), "mode" to choices(listOf("LOW","NORMAL","HIGH")))),
        definition("arm", "Authorize the final exit for 20 seconds AFTER the log is reconstructed. Choose channel A–F from the reported door seal and exit manual. Wrong choice costs 15 seconds. Does not open the door.", mapOf("roomId" to schema(64), "requestId" to schema(64), "channel" to choices(listOf("A","B","C","D","E","F"))))
    )
    fun aiTools() = tools().map { AiToolSpec(it.name, it.description, it.inputSchema) }
    private fun definition(name: String, description: String, properties: Map<String, JsonElement>) = McpToolDefinition(
        name = PREFIX + name, description = description, inputSchema = buildJsonObject {
            put("type", "object"); put("properties", JsonObject(properties)); put("required", JsonArray(properties.keys.map { JsonPrimitive(it) })); put("additionalProperties", false)
        }.toString(), readOnly = name == "observe",
        handler = McpToolHandler { args -> currentCoroutineContext().ensureActive(); call(name, args.raw) }
    )
    fun call(name: String, raw: String): McpToolResult = try {
        if (raw.length > 4096) throw GameError("INVALID_ARGUMENTS", "Arguments exceed 4096 characters.")
        val obj = try { Json.parseToJsonElement(raw) as? JsonObject } catch (_: IllegalArgumentException) { null }
            ?: throw GameError("INVALID_ARGUMENTS", "Expected a JSON object.")
        val expected = when(name) {
            "observe" -> emptySet()
            "archive" -> setOf("roomId", "query")
            "message" -> setOf("roomId", "requestId", "text")
            "calculate" -> setOf("roomId","requestId","operation","a","b")
            "route" -> setOf("roomId", "requestId", "mode")
            "arm" -> setOf("roomId", "requestId", "channel")
            else -> throw GameError("UNKNOWN_TOOL", "Unknown tool; human actions are not available to the companion.")
        }
        if (obj.keys != expected) throw GameError("INVALID_ARGUMENTS", "Provide exactly ${expected.joinToString()}. No role or seat selectors.")
        val game = session.currentEscape()
        if (name != "observe" && string(obj, "roomId") != game.id) throw GameError("STALE_ROOM", "Observe the new room first.")
        val payload = when(name) {
            "observe" -> Json.encodeToJsonElement(game.observe())
            "archive" -> Json.encodeToJsonElement(game.archive(string(obj, "query")))
            "message" -> Json.encodeToJsonElement(game.message(string(obj, "requestId"), string(obj, "text")))
            "calculate" -> Json.encodeToJsonElement(game.calculate(string(obj,"requestId"),Arithmetic.entries.find {it.name==string(obj,"operation")}
                ?: throw GameError("INVALID_OPERATION","Choose ADD, SUBTRACT, MULTIPLY or DIVIDE."),integer(obj,"a"),integer(obj,"b")))
            "route" -> Json.encodeToJsonElement(game.route(string(obj,"requestId"), PowerMode.entries.find { it.name==string(obj,"mode") }
                ?: throw GameError("INVALID_MODE","Choose LOW, NORMAL or HIGH.")))
            else -> Json.encodeToJsonElement(game.arm(string(obj, "requestId"),string(obj,"channel")))
        }
        McpToolResult(buildJsonObject { put("schemaVersion", 2); put("ok", true); put("data", payload) }.toString())
    } catch (e: GameError) {
        McpToolResult(buildJsonObject { put("schemaVersion", 2); put("ok", false); put("code", e.code); put("message", e.message) }.toString(), true)
    }
    private fun string(obj: JsonObject, key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw GameError("INVALID_ARGUMENTS", "$key must be a string.")
    private fun integer(obj:JsonObject,key:String) = (obj[key] as? JsonPrimitive)?.takeUnless {it.isString}?.intOrNull
        ?: throw GameError("INVALID_ARGUMENTS","$key must be an integer.")
    companion object {
        const val PREFIX = "blackout_v2_"
        private fun schema(max: Int) = buildJsonObject { put("type", "string"); put("minLength", 1); put("maxLength", max) }
        private fun choices(values:List<String>) = buildJsonObject { put("type","string");put("enum",JsonArray(values.map {JsonPrimitive(it)})) }
        private fun numberSchema() = buildJsonObject {put("type","integer");put("minimum",-1_000_000);put("maximum",1_000_000)}
    }
}
