package dev.eternalz.forge

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.eternalz.forge.data.SecureVault
import dev.eternalz.forge.ui.*
import org.junit.Rule
import org.junit.Test

class SignInUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun optionalPermissionsAreCollapsedUntilRequested() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = ForgeViewModel(SecureVault(context))
        compose.setContent { ForgeTheme { ConnectScreen(ForgeState(), vm, {}, oauthClientId = "test") } }
        compose.onNodeWithText("Repository deletion").assertDoesNotExist()
        compose.onNodeWithText("Choose permissions").performClick()
        compose.onNodeWithText("Repository deletion").performScrollTo().assertIsDisplayed()
    }
    @Test fun normalUsersNeverSeeAClientIdOrTokenField() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vm = ForgeViewModel(SecureVault(context))
        compose.setContent { ForgeTheme { ConnectScreen(ForgeState(), vm, {}, oauthClientId = "") } }
        compose.onNodeWithText("Continue with GitHub").assertIsDisplayed().performClick()
        compose.onNodeWithText("App owner setup needed").assertIsDisplayed()
        compose.onNodeWithText("Personal access token").assertDoesNotExist()
        compose.onNodeWithText("GitHub OAuth app client ID").assertDoesNotExist()
    }
}
