@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package dev.eternalz.forge

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.eternalz.forge.data.SecureVault
import dev.eternalz.forge.domain.*
import dev.eternalz.forge.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Test-only fixtures. No sample accounts or repositories are injected into the application. */
class DesignScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture = ForgeState(
        theme = "Light", account = Account("design-fixture", "alexchen", "Alex Chen", "test fixture"),
        repositories = listOf(
            Repository("studio-kit", "alexchen/studio-kit", "Thoughtful components for things worth building.", "Kotlin", 128, 12, false, "main", "2026-11-21"),
            Repository("orbit-api", "alexchen/orbit-api", "A small, fast API for the next big idea.", "TypeScript", 46, 4, true, "main", "2026-11-20"),
            Repository("field-notes", "alexchen/field-notes", "An open notebook for experiments and ideas.", "JavaScript", 23, 2, false, "main", "2026-11-19"),
        ),
    )
    @Test fun signInPaintsAnOpaqueLightSurface() {
        val vm = ForgeViewModel(SecureVault(context))
        compose.setContent { ForgeTheme("Light") { ConnectScreen(ForgeState(), vm, {}) } }
        compose.onNodeWithText("Your code.\nWithin reach.").assertIsDisplayed()
        compose.onNodeWithText("Connect your workspace").assertIsDisplayed()
        compose.onNodeWithText("Continue with GitHub").assertIsDisplayed()
        compose.onNodeWithText("Personal access token").assertDoesNotExist()
        val image = compose.onRoot().captureToImage()
        val background = image.toPixelMap()[2, 2]
        assertTrue("Sign-in must paint a light background rather than the Android dark window", background.red > 0.9f && background.green > 0.9f && background.blue > 0.9f)
        save("sign-in-light", image.asAndroidBitmap())
    }
    @Test fun workspaceLight() {
        val vm = ForgeViewModel(SecureVault(context))
        compose.setContent { ForgeTheme("Light") { ForgeApp(fixture, vm) } }
        compose.onNodeWithText("Ready when you are.").assertIsDisplayed()
        compose.onNodeWithText("studio-kit").assertIsDisplayed()
        save("workspace-light", compose.onRoot().captureToImage().asAndroidBitmap())
    }
    @Test fun workspaceDarkRemainsOptional() {
        val vm = ForgeViewModel(SecureVault(context))
        compose.setContent { ForgeTheme("Dark") { ForgeApp(fixture.copy(theme = "Dark"), vm) } }
        compose.onNodeWithText("Ready when you are.").assertIsDisplayed()
        save("workspace-dark", compose.onRoot().captureToImage().asAndroidBitmap())
    }
    private data class ScreenshotOutput(val extension: String, val image: Bitmap, val format: Bitmap.CompressFormat, val quality: Int)
    private fun save(name: String, bitmap: Bitmap) {
        val folder = File(context.getExternalFilesDir(null), "design-screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val preview = Bitmap.createScaledBitmap(bitmap, 320, (bitmap.height * 320f / bitmap.width).toInt(), true)
        File(folder, "$name.jpg").outputStream().use { preview.compress(Bitmap.CompressFormat.JPEG, 40, it) }
        // Gradle can uninstall the test app after a run, removing app-scoped output.
        // Persist only synthetic-fixture screenshots in MediaStore for CI retrieval.
        for ((extension, image, format, quality) in listOf(
            ScreenshotOutput("png", bitmap, Bitmap.CompressFormat.PNG, 100),
            ScreenshotOutput("jpg", preview, Bitmap.CompressFormat.JPEG, 40),
        )) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.$extension")
                put(MediaStore.Images.Media.MIME_TYPE, if(extension == "png") "image/png" else "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ForgeDesign")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use { image.compress(format, quality, it) }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    }
}
