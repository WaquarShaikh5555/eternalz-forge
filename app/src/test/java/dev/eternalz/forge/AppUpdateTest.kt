package dev.eternalz.forge

import dev.eternalz.forge.domain.AppUpdate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdateTest {
    private fun release(url: String = "https://github.com/WaquarShaikh5555/eternalz-forge/releases/download/v0.3.2/eternalz-forge-v0.3.2.apk") = JSONObject().apply {
        put("tag_name", "v0.3.2")
        put("body", "Forge-Version-Code: 5\nForge-Signing-SHA256: ${"a".repeat(64)}")
        put("assets", org.json.JSONArray().put(JSONObject().put("name", "eternalz-forge-v0.3.2.apk").put("browser_download_url", url)))
    }
    @Test fun acceptsOnlyProductApkWithSigningMetadata() { assertEquals(5, AppUpdate.parse(release()).versionCode) }
    @Test(expected = IllegalArgumentException::class) fun rejectsForeignDownload() { AppUpdate.parse(release("https://example.com/app.apk")) }
    @Test(expected = IllegalArgumentException::class) fun rejectsPrerelease() { AppUpdate.parse(release().put("prerelease", true)) }
    @Test(expected = IllegalStateException::class) fun rejectsMissingCertificate() { AppUpdate.parse(release().put("body", "Forge-Version-Code: 5")) }
}
