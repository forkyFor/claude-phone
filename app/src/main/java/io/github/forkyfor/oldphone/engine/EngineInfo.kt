package io.github.forkyfor.oldphone.engine

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView

/** Finds out which system WebView is installed, without creating one when possible. */
object EngineInfo {
    /**
     * Below this Chromium major the app warns before loading the site. It is a warning, not a
     * block: the real safety net is the blank-page check after loading. See docs/COMPATIBILITY.md
     * for how this number was chosen and what was (not) verified.
     */
    const val MIN_WEBVIEW_MAJOR = 111

    class WebViewInfo(val packageName: String?, val version: String?, val major: Int) {
        val present get() = major > 0 || version != null
        override fun toString() = "WebView ${version ?: "?"}" + (packageName?.let { " ($it)" } ?: "")
    }

    fun parseMajor(version: String?): Int =
        version?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

    fun majorFromUserAgent(ua: String?): Int =
        Regex("Chrom(?:e|ium)/(\\d+)").find(ua ?: "")?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun webView(ctx: Context): WebViewInfo {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val p = WebView.getCurrentWebViewPackage()
                if (p != null) return WebViewInfo(p.packageName, p.versionName, parseMajor(p.versionName))
            } else {
                for (name in arrayOf("com.google.android.webview", "com.android.webview", "com.android.chrome")) {
                    // Android 7.x can use Chrome as the WebView provider; the UA below is the tie-breaker.
                    if (name == "com.android.chrome" && Build.VERSION.SDK_INT !in 24..28) continue
                    val p = try { ctx.packageManager.getPackageInfo(name, 0) } catch (e: Exception) { null }
                    if (p != null && name != "com.android.chrome") {
                        return WebViewInfo(name, p.versionName, parseMajor(p.versionName))
                    }
                }
            }
            val ua = WebSettings.getDefaultUserAgent(ctx)
            val major = majorFromUserAgent(ua)
            return WebViewInfo(null, if (major > 0) major.toString() else null, major)
        } catch (e: Throwable) {
            // No WebView installed, or it is being updated right now.
            return WebViewInfo(null, null, 0)
        }
    }
}
