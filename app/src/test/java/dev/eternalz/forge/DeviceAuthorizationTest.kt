package dev.eternalz.forge

import dev.eternalz.forge.domain.DeviceAuthorization
import org.junit.Assert.*
import org.junit.Test

class DeviceAuthorizationTest {
    @Test fun publicAccessIsTheDefaultAndNeverIncludesDeletionOrWorkflowPermissions() {
        val scopes = DeviceAuthorization.scopes(false, false).split(' ').toSet()
        assertEquals(setOf("public_repo", "read:user", "notifications"), scopes)
        assertFalse(scopes.contains("repo"))
        assertFalse(scopes.contains("delete_repo"))
    }
    @Test fun broaderScopesNeedExplicitOptIn() {
        assertEquals(setOf("repo", "read:user", "notifications", "workflow"), DeviceAuthorization.scopes(true, true).split(' ').toSet())
    }
    @Test fun slowDownHonorsBothRequiredIncreaseAndGitHubsInterval() {
        assertEquals(10, DeviceAuthorization.nextInterval(5))
        assertEquals(30, DeviceAuthorization.nextInterval(5, 30))
        assertEquals(35, DeviceAuthorization.nextInterval(30, 10))
    }
    @Test fun invalidClientIdentifiersAndUrlsAreRejected() {
        assertTrue(DeviceAuthorization.validClientId("Ov23li_example123"))
        listOf("", "client id", "https://github.com", "\"injected\"", "x\n").forEach { assertFalse(DeviceAuthorization.validClientId(it)) }
    }
    @Test fun ownerSetupErrorsDoNotTellUsersToCreateTokens() {
        assertTrue(DeviceAuthorization.errorMessage("device_flow_disabled").contains("owner"))
        assertTrue(DeviceAuthorization.errorMessage("access_denied").contains("No account"))
    }
}
