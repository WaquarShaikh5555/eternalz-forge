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

    @Test fun savedApprovalTakesPrecedenceOverTheOldRejectedSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val vault = SecureVault(instrumentation.targetContext)
        vault.accounts().forEach { vault.disconnect(it.id) }
        val account = Account("999995", "fixture", "", "public_repo")
        vault.connect(account, "synthetic-old")
        vault.pendingLogin(obj("client_id" to "test", "device_code" to "synthetic-device", "user_code" to "TEST-CODE",
            "deadline" to (System.currentTimeMillis() - 1000), "access_token" to "synthetic-approved"))
        try {
            lateinit var vm: ForgeViewModel
            instrumentation.runOnMainSync {
                vm = ForgeViewModel(vault,
                    sessionApi = { GitHubApi(it, ApiTransport { _, _, _ -> error("Old session must not be loaded before saved approval") }) },
                    authApi = { token -> GitHubApi(token, ApiTransport { _, path, _ ->
                        assertEquals("synthetic-approved", token)
                        if (path == "/user") Reply(obj("id" to account.id, "login" to "fixture"), "repo", "100")
                        else Reply(org.json.JSONArray(), "repo", "100")
                    }) })
            }
            assertFalse(vm.state.value.reconnectRequired)
            assertEquals("synthetic-approved", vault.token(account.id))
            assertNull(vault.pendingLogin())
            assertEquals("repo", vm.state.value.account?.scopes)
        } finally { vault.pendingLogin(null); vault.disconnect(account.id) }
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
