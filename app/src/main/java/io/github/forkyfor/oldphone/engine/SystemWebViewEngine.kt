package io.github.forkyfor.oldphone.engine

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener

/**
 * The Android System WebView showing the official site.
 *
 * What it deliberately does NOT do: change the user agent, read cookies, intercept requests,
 * inject styles, or expose a JavaScript interface to the page.
 */
@SuppressLint("SetJavaScriptEnabled")
class SystemWebViewEngine(private val activity: Activity, private val host: EngineHost) : WebEngine {
    override val id = "webview"
    private val web = WebView(activity)
    private val info = EngineInfo.webView(activity)
    private val toolsSource: String by lazy {
        activity.assets.open("pagetools/lib.js").bufferedReader().use { it.readText() }
    }
    private var destroyed = false
    private var lastUrl: String? = null

    override val view get() = web
    override val currentUrl get() = if (destroyed) lastUrl else (web.url ?: lastUrl)

    init {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false // lets the page's own voice mode answer aloud
            setSupportMultipleWindows(false) // window.open() navigates in place and goes through the URL policy
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            // Honour the system font size (accessibility).
            textZoom = (activity.resources.configuration.fontScale * 100).toInt().coerceIn(80, 200)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true) // the session lives in the WebView's own cookie store, like in a browser
            setAcceptThirdPartyCookies(web, true) // same default as Chrome; needed by embedded sign-in/bot-check frames
        }
        web.isFocusableInTouchMode = true
        web.webViewClient = Client()
        web.webChromeClient = Chrome()
        web.setDownloadListener { url, _, contentDisposition, mime, _ ->
            host.onDownload(url, URLUtil.guessFileName(url, contentDisposition, mime))
        }
    }

    private fun handleNavigation(uri: Uri, mainFrame: Boolean): Boolean {
        return when (UrlPolicy.decide(uri.scheme, uri.host, host.extraHosts())) {
            UrlPolicy.Decision.INTERNAL -> false
            UrlPolicy.Decision.EXTERNAL -> {
                // Frames may load third-party content in place; only top-level links leave the app.
                if (mainFrame) host.onExternalLink(uri)
                mainFrame
            }
            UrlPolicy.Decision.BLOCK -> true
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            handleNavigation(request.url, request.isForMainFrame)

        @Deprecated("Only called on Android 5 and 6")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            // No way to tell frames apart here, so everything is treated as top-level.
            return handleNavigation(Uri.parse(url), true)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            lastUrl = url
            host.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            lastUrl = url
            host.onPageFinished(url)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            lastUrl = url
            host.onUrlChanged(url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            if (Build.VERSION.SDK_INT >= 23) report(error.errorCode, error.description?.toString() ?: "")
        }

        @Deprecated("Only called on Android 5")
        override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
            if (Build.VERSION.SDK_INT < 23) report(errorCode, description ?: "")
        }

        private fun report(code: Int, description: String) {
            val kind = when (code) {
                ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_TIMEOUT, ERROR_IO, ERROR_PROXY_AUTHENTICATION -> LoadError.NETWORK
                ERROR_FAILED_SSL_HANDSHAKE -> LoadError.TLS
                else -> LoadError.OTHER
            }
            host.onMainFrameError(kind, "$description ($code)")
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (request.isForMainFrame) host.onHttpStatus(response.statusCode)
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel() // never continue past a certificate problem
            if (error.url == null || error.url == view.url || UrlPolicy.isSite(Uri.parse(error.url).host)) {
                host.onMainFrameError(LoadError.TLS, "certificate error ${error.primaryError}")
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            host.onEngineCrashed() // the activity builds a new engine instead of dying with the renderer
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = host.onProgress(newProgress)

        override fun onPermissionRequest(request: PermissionRequest) {
            if (!UrlPolicy.isSite(request.origin?.host)) {
                request.deny()
                return
            }
            val audio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            val video = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            if (!audio && !video) {
                request.deny()
                return
            }
            host.onMediaRequest(audio, video) { granted ->
                if (granted) {
                    request.grant(request.resources.filter {
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    }.toTypedArray())
                } else {
                    request.deny()
                }
            }
        }

        override fun onShowFileChooser(
            webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
        ): Boolean {
            host.onFileRequest(
                FileRequest(
                    multiple = params.mode == FileChooserParams.MODE_OPEN_MULTIPLE,
                    mimeTypes = params.acceptTypes?.filter { it.isNotBlank() } ?: emptyList(),
                    capture = params.isCaptureEnabled,
                ) { uris -> callback.onReceiveValue(if (uris.isNullOrEmpty()) null else uris.toTypedArray()) }
            )
            return true
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                host.onConsoleError(message.message() ?: "")
            }
            return true
        }
    }

    override fun load(url: String) {
        if (!destroyed) web.loadUrl(url)
    }

    override fun reload() {
        if (!destroyed) web.reload()
    }

    override fun canGoBack() = !destroyed && web.canGoBack()

    override fun goBack() {
        if (!destroyed) web.goBack()
    }

    override fun onResume() {
        if (!destroyed) web.onResume()
    }

    override fun onPause() {
        if (destroyed) return
        web.onPause()
        CookieManager.getInstance().flush() // keeps the owner signed in across restarts
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        (web.parent as? android.view.ViewGroup)?.removeView(web)
        web.stopLoading()
        web.webChromeClient = null
        web.destroy()
    }

    override fun pageTool(name: String, arg: String?, result: (String?) -> Unit) {
        if (destroyed) {
            result(null)
            return
        }
        val call = "__opcTools[${JSONObject.quote(name)}](${if (arg == null) "" else JSONObject.quote(arg)})"
        val js = "(function(){try{$toolsSource\nreturn String($call);}catch(e){return null;}})()"
        web.evaluateJavascript(js) { raw ->
            val value = try {
                if (raw == null || raw == "null") null else JSONTokener(raw).nextValue() as? String
            } catch (e: Exception) {
                null
            }
            result(value)
        }
    }

    override fun clearData(everything: Boolean, done: () -> Unit) {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            if (everything && !destroyed) {
                WebStorage.getInstance().deleteAllData()
                web.clearCache(true)
                web.clearHistory()
                web.clearFormData()
            }
            done()
        }
    }

    override fun describe() = "Android System $info"
}
