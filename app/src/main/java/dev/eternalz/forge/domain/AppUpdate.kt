package dev.eternalz.forge.domain

import org.json.JSONObject

/** Only accept APK assets from this product's fixed release origin. */
data class AppUpdate(val versionCode: Int, val version: String, val url: String, val certificate: String) {
    companion object {
        fun parse(release: JSONObject): AppUpdate {
            require(!release.optBoolean("draft") && !release.optBoolean("prerelease"))
            val body = release.getString("body")
            val code = Regex("(?m)^Forge-Version-Code: ([0-9]+)$").find(body)?.groupValues?.get(1)?.toInt()
                ?: error("This release has no compatible Android update metadata.")
            val certificate = Regex("(?m)^Forge-Signing-SHA256: ([a-fA-F0-9]{64})$").find(body)?.groupValues?.get(1)?.lowercase()
                ?: error("This release has no verified signing identity metadata.")
            val version = release.getString("tag_name")
            require(version.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")))
            val name = "eternalz-forge-$version.apk"
            val expected = "https://github.com/WaquarShaikh5555/eternalz-forge/releases/download/$version/$name"
            val assets = release.getJSONArray("assets")
            require((0 until assets.length()).any { assets.getJSONObject(it).optString("name") == name && assets.getJSONObject(it).optString("browser_download_url") == expected })
            return AppUpdate(code, version, expected, certificate)
        }
    }
}
