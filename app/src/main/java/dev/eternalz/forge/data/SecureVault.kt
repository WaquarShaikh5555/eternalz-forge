package dev.eternalz.forge.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.eternalz.forge.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App-private atomic encrypted file. Only the Android Keystore holds the AES key. Backups disabled. */
class SecureVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "workspace.vault"))
    private val alias = "eternalz.forge.vault.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    private var root: JSONObject = if (file.baseFile.exists()) {
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 13) { "The encrypted vault is damaged." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        JSONObject(cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8))
    } else JSONObject()

    @Synchronized private fun persist() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(root.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    fun accounts(): List<Account> = root.optJSONArray("accounts")?.objects()?.map { Account(it.text("id"), it.text("login"), it.text("name"), it.text("scopes")) } ?: emptyList()
    fun active(): String = root.text("active")
    fun token(id: String): String = root.optJSONArray("accounts")?.objects()?.firstOrNull { it.text("id") == id }?.text("token") ?: ""
    fun connect(account: Account, token: String) {
        val all = root.optJSONArray("accounts")?.objects()?.filter { it.text("id") != account.id }.orEmpty()
        root.put("accounts", JSONArray(all + obj("id" to account.id, "login" to account.login, "name" to account.name, "scopes" to account.scopes, "token" to token)))
        root.put("active", account.id); persist()
    }
    fun switch(id: String) { require(accounts().any { it.id == id }); root.put("active", id); persist() }
    fun disconnect(id: String) {
        root.put("accounts", JSONArray(root.optJSONArray("accounts")?.objects()?.filter { it.text("id") != id }.orEmpty()))
        root.remove("draft:$id"); root.remove("recent:$id"); root.remove("pins:$id")
        if (active() == id) root.put("active", accounts().firstOrNull()?.id ?: "")
        persist()
    }
    fun preference(key: String, default: String = "") = root.optString("pref:$key", default)
    fun preference(key: String, value: String, save: Boolean) { root.put("pref:$key", value); if (save) persist() }
    fun recent(id: String): List<String> = root.optJSONArray("recent:$id")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    fun visit(id: String, repo: String) { root.put("recent:$id", JSONArray((listOf(repo) + recent(id)).distinct().take(12))); persist() }
    fun pins(id: String): List<String> = root.optJSONArray("pins:$id")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
    fun pin(id: String, repo: String) { val old = pins(id); root.put("pins:$id", JSONArray(if (repo in old) old - repo else old + repo)); persist() }
    fun draft(id: String): ChangeSet? = root.optJSONObject("draft:$id")?.let { d ->
        ChangeSet(d.text("repository"), d.text("branch"), d.text("base"), d.getJSONArray("files").objects().map {
            Draft(it.text("path"), it.text("original"), if (it.isNull("content")) null else it.text("content"), it.text("sha"), it.optString("mode", "100644"))
        })
    }
    fun draft(id: String, draft: ChangeSet?) {
        if (draft == null) root.remove("draft:$id") else root.put("draft:$id", obj("repository" to draft.repository, "branch" to draft.branch, "base" to draft.base,
            "files" to JSONArray(draft.files.map { obj("path" to it.path, "original" to it.original, "content" to it.content, "sha" to it.sha, "mode" to it.mode) })))
        persist()
    }
}
