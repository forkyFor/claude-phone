package io.github.forkyfor.oldphone.engine

import android.net.Uri
import android.view.View

/** What the page asked for when it opened a file picker. */
class FileRequest(
    val multiple: Boolean,
    val mimeTypes: List<String>,
    val capture: Boolean,
    /** Call exactly once; null or empty means "cancelled". */
    val deliver: (List<Uri>?) -> Unit,
)

enum class LoadError { NETWORK, TLS, HTTP, OTHER }

/** Callbacks from an engine to the activity. Always invoked on the main thread. */
interface EngineHost {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String)
    fun onUrlChanged(url: String)
    fun onProgress(percent: Int)
    fun onMainFrameError(kind: LoadError, detail: String)
    fun onHttpStatus(code: Int)
    fun onExternalLink(uri: Uri)
    fun onFileRequest(request: FileRequest)
    /** The page (official site only) wants the microphone and/or camera. */
    fun onMediaRequest(audio: Boolean, video: Boolean, result: (Boolean) -> Unit)
    fun onDownload(url: String, fileName: String?)
    fun onDownloadSaved(uri: Uri, fileName: String)
    fun onEngineCrashed()
    fun onConsoleError(message: String)
    fun extraHosts(): Set<String>
}

/**
 * The embedded browser engine. Two implementations: the system WebView (all flavors) and
 * GeckoView (gecko flavor). The engine shows the official site as is; the only page-level
 * helpers are the three opt-in "page tools" in assets/pagetools/lib.js.
 */
interface WebEngine {
    val id: String
    val view: View
    val currentUrl: String?
    fun load(url: String)
    fun reload()
    fun canGoBack(): Boolean
    fun goBack()
    fun onResume()
    fun onPause()
    fun destroy()
    /** Runs one function of assets/pagetools/lib.js in the page; the result is null on failure. */
    fun pageTool(name: String, arg: String?, result: (String?) -> Unit)
    /** Removes cookies only (signs this device out) or everything stored by the site. */
    fun clearData(everything: Boolean, done: () -> Unit)
    /** Human readable engine name and version for the owner menu and error screens. */
    fun describe(): String
}
