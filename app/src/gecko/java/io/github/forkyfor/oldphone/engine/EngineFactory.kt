package io.github.forkyfor.oldphone.engine

import android.app.Activity

/** Gecko flavor: the system WebView or the bundled GeckoView, chosen in the owner menu. */
object EngineFactory {
    val available: List<String> = listOf("webview", "gecko")

    fun create(id: String, activity: Activity, host: EngineHost): WebEngine =
        if (id == "gecko") GeckoEngine(activity, host) else SystemWebViewEngine(activity, host)

    fun geckoVersion(): String? = GeckoEngine.version()
}
