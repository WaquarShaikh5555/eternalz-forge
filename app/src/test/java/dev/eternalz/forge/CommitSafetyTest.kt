package dev.eternalz.forge

import dev.eternalz.forge.data.*
import dev.eternalz.forge.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CommitSafetyTest {
    private class Fake(private val moved: Boolean = false, private val race: Boolean = false) : ApiTransport {
        val writes = mutableListOf<Pair<String, JSONObject?>>()
        override suspend fun execute(method: String, path: String, body: JSONObject?): Reply {
            if (method != "GET") writes += path to body
            val result = when {
                path.contains("/git/ref/heads/") -> obj("object" to obj("sha" to if(moved) "newer-head" else "base"))
                path.endsWith("/git/commits/base") -> obj("tree" to obj("sha" to "base-tree"))
                path.endsWith("/git/blobs") -> obj("sha" to "blob")
                path.endsWith("/git/trees") -> obj("sha" to "new-tree")
                path.endsWith("/git/commits") -> obj("sha" to "new-commit")
                path.contains("/git/refs/heads/") -> { if(race) throw ApiFailure(422, "Not a fast forward"); obj() }
                else -> error("Unexpected $method $path")
            }
            return Reply(result, "", "")
        }
    }
    private fun changes() = ChangeSet("owner/repo", "feature/mobile", "base", listOf(Draft("run.sh", "old", "new", "old-blob", "100755"), Draft("removed.txt", "text", null, "removed-blob")))
    @Test fun movedBranchProducesNoWrites() = runBlocking {
        val transport = Fake(moved = true)
        try { GitHubApi(transport = transport).commit(changes(), "message"); fail("Expected conflict") } catch(e: ApiFailure) { assertEquals(409, e.status) }
        assertTrue(transport.writes.isEmpty())
    }
    @Test fun atomicCommitPreservesModesAndNeverForces() = runBlocking {
        val transport = Fake()
        assertEquals("new-commit", GitHubApi(transport = transport).commit(changes(), "message"))
        val tree = transport.writes.first { it.first.endsWith("/trees") }.second!!.getJSONArray("tree")
        assertEquals("100755", tree.getJSONObject(0).getString("mode"))
        assertTrue(tree.getJSONObject(1).isNull("sha"))
        val ref = transport.writes.last()
        assertTrue(ref.first.endsWith("/refs/heads/feature/mobile"))
        assertFalse(ref.second!!.getBoolean("force"))
    }
    @Test fun concurrentPushFailureIsNotRetriedWithForce() = runBlocking {
        val transport = Fake(race = true)
        try { GitHubApi(transport = transport).commit(changes(), "message"); fail("Expected ref conflict") } catch(e: ApiFailure) { assertEquals(422, e.status) }
        assertEquals(1, transport.writes.count { it.first.contains("/refs/heads/") })
        assertFalse(transport.writes.last().second!!.getBoolean("force"))
    }
}
