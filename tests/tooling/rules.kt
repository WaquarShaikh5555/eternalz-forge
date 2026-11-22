package fixtures

fun unsafe(client: Client, context: Context) {
    // ruleid: android-global-scope
    GlobalScope.launch { work() }
    // ruleid: android-blocking-sleep
    Thread.sleep(100)
    // ruleid: android-webview-debugging
    WebView.setWebContentsDebuggingEnabled(true)
    // ruleid: android-unsafe-hostname-verifier
    client.hostnameVerifier { _, _ -> true }
    // ruleid: android-world-readable
    context.openFileOutput("private", Context.MODE_WORLD_READABLE)
}
fun safe(context: Context) {
    // ok: android-global-scope
    viewModelScope.launch { work() }
    // ok: android-blocking-sleep
    delay(100)
    // ok: android-webview-debugging
    WebView.setWebContentsDebuggingEnabled(false)
    // ok: android-unsafe-hostname-verifier
    client.hostnameVerifier { host, session -> verifier.verify(host, session) }
    // ok: android-world-readable
    context.openFileOutput("private", Context.MODE_PRIVATE)
}
