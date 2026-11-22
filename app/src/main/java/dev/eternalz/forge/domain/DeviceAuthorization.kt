package dev.eternalz.forge.domain

/** OAuth device flow uses a PUBLIC client identifier, never an embedded client secret. */
object DeviceAuthorization {
    fun validClientId(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_.-]{1,128}"))

    fun scopes(privateRepositories: Boolean, workflowFiles: Boolean, deleteRepositories: Boolean = false): String = buildList {
        add(if (privateRepositories) "repo" else "public_repo")
        add("read:user")
        add("notifications")
        if (workflowFiles) add("workflow")
        if (deleteRepositories) add("delete_repo")
    }.joinToString(" ")

    fun nextInterval(current: Int, serverInterval: Int = 0): Int = maxOf(current + 5, serverInterval)

    fun errorMessage(code: String): String = when(code) {
        "device_flow_disabled" -> "GitHub sign-in is not enabled for this app registration. The Eternalz Forge owner needs to enable Device Flow in GitHub's app settings. You do not need to create a token."
        "incorrect_client_credentials" -> "This build's GitHub app registration is invalid. Contact the app owner; do not create a token to fix this."
        "access_denied" -> "GitHub authorization was declined. No account was connected. You can try again."
        "expired_token", "token_expired" -> "Your GitHub sign-in code expired. Start again for a new code."
        "incorrect_device_code" -> "GitHub could not validate this sign-in attempt. Cancel and start again."
        else -> "GitHub sign-in could not complete. Please try again."
    }
}
