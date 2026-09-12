package ai.rever.boss.plugin.dynamic.blackout

import ai.rever.boss.plugin.dynamic.blackout.application.Session
import ai.rever.boss.plugin.dynamic.blackout.mcp.BlackoutTools
import ai.rever.boss.plugin.dynamic.blackout.engine.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolsTest {

    private fun seated(): Pair<Session, BlackoutTools> {
        val session = Session()
        session.start(Difficulty.OPERATOR, seed = 7L)
        return session to BlackoutTools(session)
    }

    @Test
    fun `the four tools are the whole surface`() {
        val (_, tools) = seated()
        assertEquals(
            listOf("blackout_v1_observe", "blackout_v1_archive", "blackout_v1_message", "blackout_v1_mark"),
            tools.tools().map { it.name }
        )
    }

    @Test
    fun `the gateway seat is given exactly the MCP definitions`() {
        // If these ever drift, the in-app seat is quietly playing an easier game.
        val (_, tools) = seated()
        val mcp = tools.tools()
        val ai = tools.aiTools()
        assertEquals(mcp.size, ai.size)
        mcp.zip(ai).forEach { (definition, spec) ->
            assertEquals(definition.name, spec.name)
            assertEquals(definition.description, spec.description)
            assertEquals(definition.inputSchema, spec.inputSchema)
        }
    }

    @Test
    fun `observe carries no physical state and no map`() {
        val (session, tools) = seated()
        val game = session.current()
        game.inspect(game.case.sites.first().id)
        val body = tools.call("observe", "{}").text
        assertTrue(body.contains("\"ok\":true"))
        game.case.sites.forEach { site ->
            assertFalse(body.contains(site.physical), "a physical state reached the agent through observe")
        }
    }

    @Test
    fun `malformed and over-specified arguments are refused`() {
        val (_, tools) = seated()
        listOf(
            "observe" to "not json",
            "observe" to "[]",
            "observe" to "{\"seat\":\"pilot\"}",
            "archive" to "{}",
            "archive" to "{\"query\":1}",
            "message" to "{\"requestId\":\"a\"}",
            "message" to "{\"requestId\":\"a\",\"text\":\"hi\",\"team\":\"BLUE\"}",
            "mark" to "{\"requestId\":\"a\",\"compartment\":\"Bridge\",\"verdict\":\"MAYBE\",\"reason\":\"x\"}"
        ).forEach { (name, args) ->
            val result = tools.call(name, args)
            assertTrue(result.isError, "$name accepted $args")
            assertTrue(result.text.contains("\"ok\":false"))
        }
    }

    @Test
    fun `an unknown tool is refused`() {
        val (_, tools) = seated()
        val result = tools.call("solve", "{}")
        assertTrue(result.isError)
        assertTrue(result.text.contains("UNKNOWN_TOOL"))
    }

    @Test
    fun `there is no tool that names the culprit`() {
        // Calling the case is the human's move. An agent must persuade, not decide.
        val (_, tools) = seated()
        assertTrue(tools.tools().none { it.name.contains("accuse") || it.name.contains("solve") })
    }

    @Test
    fun `an oversized argument blob is refused before parsing`() {
        val (_, tools) = seated()
        val result = tools.call("message", "{\"requestId\":\"a\",\"text\":\"" + "x".repeat(5000) + "\"}")
        assertTrue(result.isError)
        assertTrue(result.text.contains("INVALID_ARGUMENTS"))
    }

    @Test
    fun `tools report a clean error when no case is open`() {
        val tools = BlackoutTools(Session())
        val result = tools.call("observe", "{}")
        assertTrue(result.isError)
        assertTrue(result.text.contains("NO_CASE"))
    }

    @Test
    fun `archive and mark work end to end through the tool surface`() {
        val (session, tools) = seated()
        val archive = tools.call("archive", "{\"query\":\"ALL\"}")
        assertFalse(archive.isError, archive.text)
        assertTrue(archive.text.contains("ROSTER"))

        val name = session.current().case.sites.first().name
        val mark = tools.call(
            "mark",
            "{\"requestId\":\"m1\",\"compartment\":\"$name\",\"verdict\":\"SUSPECT\",\"reason\":\"signed off overnight\"}"
        )
        assertFalse(mark.isError, mark.text)
        assertEquals("SUSPECT", session.current().pilotView().sites.first { it.name == name }.mark?.verdict?.name)
    }
}
