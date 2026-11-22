package dev.eternalz.forge

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.eternalz.forge.data.SecureVault
import dev.eternalz.forge.domain.*
import dev.eternalz.forge.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SecurityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun destructiveActionRequiresExactConfirmation() {
        var submitted: Map<String, String>? = null
        lateinit var clipboard: androidx.compose.ui.platform.ClipboardManager
        compose.setContent {
            clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            ForgeTheme { ActionSheet(Form("deleteRepo", "Delete repository", confirmation = "Waquarshaikh/xx", target = "Waquarshaikh/xx"), false, {}, { submitted = it }) }
        }
        compose.onNodeWithText("Waquarshaikh/xx").assertIsDisplayed()
        compose.onNodeWithText("Delete repository permanently").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Copy repository name").performClick()
        compose.runOnIdle { assertEquals("Waquarshaikh/xx", clipboard.getText()?.text) }
        compose.onNodeWithText("Delete repository permanently").assertIsNotEnabled()
        compose.onNodeWithText("Type Waquarshaikh/xx to confirm").performTextInput("xx")
        compose.onNodeWithText("Delete repository permanently").assertIsNotEnabled()
        compose.onNodeWithText("Type Waquarshaikh/xx to confirm").performTextClearance()
        compose.onNodeWithText("Type Waquarshaikh/xx to confirm").performTextInput("Waquarshaikh/xx")
        compose.onNodeWithText("Delete repository permanently").assertIsEnabled().performClick()
        assertEquals("Waquarshaikh/xx", submitted?.get("__confirmation"))
    }

    @Test fun missingConfirmationIsRejectedBeforeAnyRemoteCall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnIdle {
            val vm = ForgeViewModel(SecureVault(context))
            vm.apply(Form("deleteRepo", "Delete", confirmation = "owner/repo", target = "owner/repo"), emptyMap())
            assertEquals("Enter the exact confirmation text before continuing.", vm.state.value.error)
            assertFalse(vm.state.value.busy)
        }
    }

    @Test fun accountDraftsAndCredentialsAreIsolated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = SecureVault(context)
        val a = Account("test-a", "account-a", "", "test")
        val b = Account("test-b", "account-b", "", "test")
        try {
            vault.connect(a, "test-credential-a")
            vault.draft(a.id, ChangeSet("a/repo", "main", "base", listOf(Draft("file", "before", "after"))))
            vault.connect(b, "test-credential-b")
            assertNull(vault.draft(b.id))
            assertEquals("test-credential-a", vault.token(a.id))
            val disk = java.io.File(context.noBackupFilesDir, "workspace.vault").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(disk.contains("test-credential-a"))
            assertFalse(disk.contains("before"))
            val restored = SecureVault(context)
            assertEquals("after", restored.draft(a.id)!!.files.first().content)
            restored.disconnect(a.id)
            assertEquals("test-credential-b", restored.token(b.id))
            assertNull(restored.draft(a.id))
        } finally { vault.disconnect(a.id); vault.disconnect(b.id) }
    }
}
