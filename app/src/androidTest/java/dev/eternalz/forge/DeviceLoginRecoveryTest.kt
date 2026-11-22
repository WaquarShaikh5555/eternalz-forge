package dev.eternalz.forge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.eternalz.forge.data.*
import dev.eternalz.forge.ui.ForgeViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DeviceLoginRecoveryTest {
    @Test fun browserApprovalCompletesExchangeAndStoresAccount() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val vault = SecureVault(instrumentation.targetContext)
        vault.accounts().forEach { vault.disconnect(it.id) }
        vault.pendingLogin(obj("client_id" to "test", "device_code" to "synthetic-device",
            "user_code" to "TEST-CODE", "deadline" to (System.currentTimeMillis() + 60000), "interval" to 5))
        val polls = AtomicInteger()
        lateinit var vm: ForgeViewModel
        instrumentation.runOnMainSync {
            vm = ForgeViewModel(vault) { token -> GitHubApi(token, ApiTransport { _, path, body ->
                when (path) {
                    "/login/oauth/access_token" -> {
                        assertEquals("", token)
                        assertEquals("synthetic-device", body?.getString("device_code"))
                        assertEquals("urn:ietf:params:oauth:grant-type:device_code", body?.getString("grant_type"))
                        if (polls.incrementAndGet() == 1) Reply(obj("error" to "authorization_pending"), "", "")
                        else Reply(obj("access_token" to "synthetic-approved"), "", "")
                    }
                    "/user" -> {
                        assertEquals("synthetic-approved", token)
                        Reply(obj("id" to 999998, "login" to "approved", "name" to "Fixture"), "read:user", "100")
                    }
                    else -> Reply(org.json.JSONArray(), "", "")
                }
            }) }
        }
        try {
            val end = System.currentTimeMillis() + 15000
            while (vm.state.value.account == null && System.currentTimeMillis() < end) Thread.sleep(100)
            assertEquals("approved", vm.state.value.account?.login)
            assertEquals(2, polls.get())
            assertNull(vault.pendingLogin())
        } finally {
            instrumentation.runOnMainSync { vm.cancelLogin() }
            vault.disconnect("999998")
        }
    }
    @Test fun approvedGrantSurvivesRecreationAndTransientNetworkFailure() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val vault = SecureVault(instrumentation.targetContext)
        vault.accounts().forEach { vault.disconnect(it.id) }
        // Models a consumed grant already exchanged before Android reclaimed the app.
        vault.pendingLogin(obj("client_id" to "test", "device_code" to "synthetic-device",
            "user_code" to "TEST-CODE", "deadline" to (System.currentTimeMillis() - 1000),
            "access_token" to "synthetic-not-a-credential", "interval" to 5))
        val attempts = AtomicInteger()
        lateinit var vm: ForgeViewModel
        instrumentation.runOnMainSync {
            vm = ForgeViewModel(vault) { token ->
                assertEquals("synthetic-not-a-credential", token)
                GitHubApi(token, ApiTransport { _, path, _ ->
                    if (path == "/user") {
                        if (attempts.incrementAndGet() == 1) throw IOException("synthetic offline")
                        Reply(obj("id" to 999999, "login" to "fixture", "name" to "Fixture"), "read:user", "100")
                    } else Reply(org.json.JSONArray(), "read:user", "100")
                })
            }
        }
        try {
            val end = System.currentTimeMillis() + 15000
            while (vm.state.value.account == null && System.currentTimeMillis() < end) Thread.sleep(100)
            assertEquals("fixture", vm.state.value.account?.login)
            assertEquals(2, attempts.get())
            assertNull(vault.pendingLogin())
            assertEquals("synthetic-not-a-credential", vault.token("999999"))
        } finally {
            instrumentation.runOnMainSync { vm.cancelLogin() }
            vault.disconnect("999999")
        }
    }
}
