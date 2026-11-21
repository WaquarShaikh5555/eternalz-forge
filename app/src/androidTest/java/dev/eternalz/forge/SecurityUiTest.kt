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
        var submitted = false
        compose.setContent { ForgeTheme { ActionSheet(Form("deleteRepo", "Delete repository", confirmation = "owner/repo"), false, {}, { submitted = true }) } }
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Type owner/repo to confirm").performTextInput("owner/repo")
        compose.onNodeWithText("Confirm").assertIsEnabled().performClick()
        assertTrue(submitted)
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
