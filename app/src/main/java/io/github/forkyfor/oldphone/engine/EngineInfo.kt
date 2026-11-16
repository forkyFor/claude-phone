package io.github.forkyfor.oldphone.engine

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView

/** Finds out which system WebView is installed, without creating one when possible. */
object EngineInfo {
    /**
     * Below this Chromium major the app warns before loading the site ("Try anyway" stays
     * available). History of this number, see docs/COMPATIBILITY.md: emulators showed the
     * sign-in page appearing on WebView 83, but the first real phone (Redmi Note 7) got a
     * half-styled page and the site's own "browser is out of date" notice. 111 is the first
     * version that passes [selfTest], which checks the web features modern sites rely on.
     */
    const val MIN_WEBVIEW_MAJOR = 111

    private const val SELF_TEST_JS = "(function(){try{var c=window.CSS;return String(!!(c&&c.supports&&" +
        "c.supports('color','color-mix(in srgb, red, blue)')&&c.supports('selector(:has(a))')&&" +
        "typeof CSSLayerBlockRule!=='undefined'&&typeof structuredClone==='function'&&[].at));}" +
        "catch(e){return 'false';}})()"

    /**
     * Asks the installed WebView itself whether it has the features a current site needs
     * (CSS color-mix, :has(), cascade layers, structuredClone, Array.at). It loads an empty
     * local page: nothing is fetched from the network. Result: true = recent enough,
     * false = too old, null = no usable WebView.
     */
    fun selfTest(ctx: Context, done: (Boolean?) -> Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        try {
            val web = WebView(ctx)
            var finished = false
            fun finish(result: Boolean?) {
                if (finished) return
                finished = true
                try { web.destroy() } catch (e: Throwable) { }
                done(result)
            }
            @android.annotation.SuppressLint("SetJavaScriptEnabled")
            web.settings.javaScriptEnabled = true
            web.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(SELF_TEST_JS) { r -> finish(r?.contains("true") == true) }
                }
            }
            web.loadDataWithBaseURL("https://selftest.invalid/", "<!doctype html><title>t</title>", "text/html", "utf-8", null)
            handler.postDelayed({ finish(null) }, 8000L)
        } catch (t: Throwable) {
            handler.post { done(null) }
        }
    }

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
