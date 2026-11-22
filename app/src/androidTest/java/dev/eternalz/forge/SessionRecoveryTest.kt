package dev.eternalz.forge

import androidx.test.platform.app.InstrumentationRegistry
import dev.eternalz.forge.data.*
import dev.eternalz.forge.domain.*
import dev.eternalz.forge.ui.*
import org.junit.Assert.*
import org.junit.Test

class SessionRecoveryTest {
    @Test fun rejectedSavedCredentialCanReconnectWithoutLosingDrafts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val vault = SecureVault(instrumentation.targetContext)
        vault.accounts().forEach { vault.disconnect(it.id) }
        vault.pendingLogin(null)
        val account = Account("999997", "fixture", "Fixture", "public_repo")
        vault.connect(account, "synthetic-old")
        vault.draft(account.id, ChangeSet("fixture/repo", "main", "base", listOf(Draft("file", "before", "draft"))))
        lateinit var vm: ForgeViewModel
        try {
            instrumentation.runOnMainSync {
                vm = ForgeViewModel(vault, sessionApi = { token -> GitHubApi(token, ApiTransport { _, path, _ ->
                    if (token == "synthetic-old") throw ApiFailure(401, "Synthetic rejected credential")
                    when (path) {
                        "/user" -> Reply(obj("id" to account.id, "login" to "fixture", "name" to "Fixture"), "public_repo", "100")
                        else -> Reply(org.json.JSONArray(), "public_repo", "100")
                    }
                }) })
            }
            assertTrue(vm.state.value.reconnectRequired)
            assertEquals("draft", vault.draft(account.id)?.files?.first()?.content)
            assertEquals("synthetic-old", vault.token(account.id))
            instrumentation.runOnMainSync { vm.connect("synthetic-new") }
            assertFalse(vm.state.value.reconnectRequired)
            assertEquals("synthetic-new", vault.token(account.id))
            assertEquals("draft", vm.state.value.draft?.files?.first()?.content)
            assertNull(vm.state.value.error)
        } finally { vault.disconnect(account.id) }
    }

    @Test fun permissionDeniedDoesNotClaimThatCredentialWasRevoked() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val vault = SecureVault(instrumentation.targetContext)
        vault.accounts().forEach { vault.disconnect(it.id) }
        vault.pendingLogin(null)
        val account = Account("999996", "fixture", "", "public_repo")
        vault.connect(account, "synthetic-valid-but-forbidden")
        try {
            lateinit var vm: ForgeViewModel
            instrumentation.runOnMainSync {
                vm = ForgeViewModel(vault, sessionApi = { token -> GitHubApi(token, ApiTransport { _, path, _ ->
                    if (path == "/user") Reply(obj("id" to account.id, "login" to "fixture"), "public_repo", "100")
                    else throw ApiFailure(403, "Synthetic permission denied")
                }) })
            }
            assertFalse(vm.state.value.reconnectRequired)
            assertEquals("Synthetic permission denied", vm.state.value.error)
            assertEquals("synthetic-valid-but-forbidden", vault.token(account.id))
        } finally { vault.disconnect(account.id) }
    }
}
