package io.github.forkyfor.oldphone.engine

import android.app.Activity

/** Lite flavor: the system WebView is the only engine. */
object EngineFactory {
    val available: List<String> = listOf("webview")

    fun create(id: String, activity: Activity, host: EngineHost): WebEngine =
        SystemWebViewEngine(activity, host)

    fun geckoVersion(): String? = null
}
