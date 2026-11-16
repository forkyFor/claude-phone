package io.github.forkyfor.oldphone.engine

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import io.github.forkyfor.oldphone.FilesProvider
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.io.File

/**
 * GeckoView (the engine of Firefox for Android) showing the official site. It does not depend
 * on the system WebView, so it helps on phones whose WebView can no longer be updated, as
 * long as they run Android 8.0 or newer. Like the WebView engine it changes nothing in the page.
 */
class GeckoEngine(private val activity: Activity, private val host: EngineHost) : WebEngine {
    override val id = "gecko"
    private val geckoView = GeckoView(activity)
    private val session = GeckoSession(
        GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .build()
    )
    private val main = Handler(Looper.getMainLooper())
    private var url: String? = null
    private var canBack = false
    private var destroyed = false
    private var port: WebExtension.Port? = null
    private val waiting = HashMap<Int, (String?) -> Unit>()
    private var nextCall = 1

    override val view get() = geckoView
    override val currentUrl get() = url

    init {
        val rt = runtime(activity)
        session.navigationDelegate = Navigation()
        session.progressDelegate = Progress()
        session.contentDelegate = Content()
        session.permissionDelegate = Permissions()
        session.promptDelegate = Prompts()
        session.open(rt)
        geckoView.setSession(session)
        rt.webExtensionController
            .ensureBuiltIn("resource://android/assets/pagetools/", "pagetools@oldphone.invalid")
            .accept({ ext ->
                if (ext != null && !destroyed) {
                    session.webExtensionController.setMessageDelegate(ext, Messages(), "opc")
                }
            }, { e -> Log.w(TAG, "page tools unavailable", e) })
    }

    private inner class Navigation : GeckoSession.NavigationDelegate {
        override fun onLoadRequest(
            session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest
        ): GeckoResult<AllowOrDeny> {
            val uri = Uri.parse(request.uri)
            return when (UrlPolicy.decide(uri.scheme, uri.host, host.extraHosts())) {
                UrlPolicy.Decision.INTERNAL -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
                UrlPolicy.Decision.EXTERNAL -> {
                    host.onExternalLink(uri)
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                UrlPolicy.Decision.BLOCK -> GeckoResult.fromValue(AllowOrDeny.DENY)
            }
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            // target=_blank and window.open(): same policy, in the same view.
            val u = Uri.parse(uri)
            when (UrlPolicy.decide(u.scheme, u.host, host.extraHosts())) {
                UrlPolicy.Decision.INTERNAL -> session.loadUri(uri)
                UrlPolicy.Decision.EXTERNAL -> host.onExternalLink(u)
                UrlPolicy.Decision.BLOCK -> {}
            }
            return null
        }

        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            canBack = canGoBack
        }

        override fun onLocationChange(
            session: GeckoSession, newUrl: String?, perms: MutableList<PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean
        ) {
            url = newUrl
            if (newUrl != null) host.onUrlChanged(newUrl)
        }

        override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
            val kind = when (error.category) {
                WebRequestError.ERROR_CATEGORY_NETWORK, WebRequestError.ERROR_CATEGORY_URI,
                WebRequestError.ERROR_CATEGORY_PROXY -> LoadError.NETWORK
                WebRequestError.ERROR_CATEGORY_SECURITY -> LoadError.TLS
                else -> LoadError.OTHER
            }
            host.onMainFrameError(kind, "gecko error ${error.code}")
            return null
        }
    }

    private inner class Progress : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, pageUrl: String) {
            url = pageUrl
            host.onPageStarted(pageUrl)
        }

        override fun onPageStop(session: GeckoSession, success: Boolean) {
            host.onPageFinished(url ?: "")
        }

        override fun onProgressChange(session: GeckoSession, progress: Int) = host.onProgress(progress)
    }

    private inner class Content : GeckoSession.ContentDelegate {
        override fun onCrash(session: GeckoSession) = host.onEngineCrashed()
        override fun onKill(session: GeckoSession) = host.onEngineCrashed()

        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            saveDownload(response)
        }
    }

    private inner class Permissions : PermissionDelegate {
        override fun onAndroidPermissionsRequest(
            session: GeckoSession, permissions: Array<out String>?, callback: PermissionDelegate.Callback
        ) {
            val list = permissions ?: emptyArray()
            host.onMediaRequest(
                list.contains(Manifest.permission.RECORD_AUDIO), list.contains(Manifest.permission.CAMERA)
            ) { granted -> if (granted) callback.grant() else callback.reject() }
        }

        override fun onMediaPermissionRequest(
            session: GeckoSession, uri: String, video: Array<out PermissionDelegate.MediaSource>?,
            audio: Array<out PermissionDelegate.MediaSource>?, callback: PermissionDelegate.MediaCallback
        ) {
            if (!UrlPolicy.isSite(Uri.parse(uri).host)) {
                callback.reject()
                return
            }
            host.onMediaRequest(!audio.isNullOrEmpty(), !video.isNullOrEmpty()) { granted ->
                if (granted) callback.grant(video?.firstOrNull(), audio?.firstOrNull()) else callback.reject()
            }
        }

        override fun onContentPermissionRequest(
            session: GeckoSession, perm: PermissionDelegate.ContentPermission
        ): GeckoResult<Int> {
            val allow = when (perm.permission) {
                // Lets the page's own voice mode answer aloud and keep its data, as in a browser.
                PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE, PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
                PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> UrlPolicy.isSite(Uri.parse(perm.uri).host)
                else -> false // location, notifications, sensors...
            }
            return GeckoResult.fromValue(
                if (allow) PermissionDelegate.ContentPermission.VALUE_ALLOW else PermissionDelegate.ContentPermission.VALUE_DENY
            )
        }
    }

    /** Native versions of the few browser dialogs a page can ask for. */
    private inner class Prompts : PromptDelegate {
        override fun onFilePrompt(
            session: GeckoSession, prompt: PromptDelegate.FilePrompt
        ): GeckoResult<PromptDelegate.PromptResponse> {
            val result = GeckoResult<PromptDelegate.PromptResponse>()
            val multiple = prompt.type == PromptDelegate.FilePrompt.Type.MULTIPLE
            host.onFileRequest(
                FileRequest(
                    multiple = multiple,
                    mimeTypes = prompt.mimeTypes?.filter { it.isNotBlank() } ?: emptyList(),
                    capture = prompt.capture != PromptDelegate.FilePrompt.Capture.NONE,
                ) { uris ->
                    result.complete(
                        when {
                            uris.isNullOrEmpty() -> prompt.dismiss()
                            multiple -> prompt.confirm(activity, uris.toTypedArray())
                            else -> prompt.confirm(activity, uris[0])
                        }
                    )
                }
            )
            return result
        }

        override fun onAlertPrompt(
            session: GeckoSession, prompt: PromptDelegate.AlertPrompt
        ): GeckoResult<PromptDelegate.PromptResponse> {
            val result = GeckoResult<PromptDelegate.PromptResponse>()
            AlertDialog.Builder(activity).setTitle(prompt.title).setMessage(prompt.message)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener { result.complete(prompt.dismiss()) }.show()
            return result
        }

        override fun onButtonPrompt(
            session: GeckoSession, prompt: PromptDelegate.ButtonPrompt
        ): GeckoResult<PromptDelegate.PromptResponse> {
            val result = GeckoResult<PromptDelegate.PromptResponse>()
            var answered = false
            AlertDialog.Builder(activity).setTitle(prompt.title).setMessage(prompt.message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    answered = true
                    result.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE))
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    answered = true
                    result.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE))
                }
                .setOnDismissListener { if (!answered) result.complete(prompt.dismiss()) }.show()
            return result
        }

        override fun onChoicePrompt(
            session: GeckoSession, prompt: PromptDelegate.ChoicePrompt
        ): GeckoResult<PromptDelegate.PromptResponse> {
            val result = GeckoResult<PromptDelegate.PromptResponse>()
            val choices = prompt.choices.filter { !it.separator && !it.disabled && it.items == null }
            var answered = false
            AlertDialog.Builder(activity).setTitle(prompt.title)
                .setItems(choices.map { it.label }.toTypedArray()) { _, which ->
                    answered = true
                    result.complete(prompt.confirm(choices[which]))
                }
                .setOnDismissListener { if (!answered) result.complete(prompt.dismiss()) }.show()
            return result
        }
    }

    private inner class Messages : WebExtension.MessageDelegate {
        override fun onConnect(newPort: WebExtension.Port) {
            port = newPort
            newPort.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, p: WebExtension.Port) {
                    val o = message as? JSONObject ?: return
                    val cb = waiting.remove(o.optInt("id")) ?: return
                    cb(if (o.isNull("result")) null else o.optString("result"))
                }

                override fun onDisconnect(p: WebExtension.Port) {
                    if (port === p) port = null
                }
            })
        }
    }

    /** Downloads are streamed by the engine itself, so no cookie ever has to leave it. */
    private fun saveDownload(response: WebResponse) {
        val body = response.body ?: return
        val disposition = response.headers["Content-Disposition"] ?: response.headers["content-disposition"] ?: ""
        val name = (Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?").find(disposition)?.groupValues?.get(1)
            ?: Uri.parse(response.uri).lastPathSegment ?: "download")
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").takeLast(80).ifBlank { "download" }
        val app: Context = activity.applicationContext
        Thread {
            val saved: Uri? = try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        response.headers["Content-Type"]?.let { put(MediaStore.Downloads.MIME_TYPE, it.substringBefore(';')) }
                    }
                    val target = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (target != null) {
                        app.contentResolver.openOutputStream(target)?.use { out -> body.use { it.copyTo(out) } }
                    }
                    target
                } else {
                    val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: FilesProvider.dir(app)
                    val file = File(dir, "${System.currentTimeMillis()}_$name")
                    file.outputStream().use { out -> body.use { it.copyTo(out) } }
                    FilesProvider.uriFor(app, file)
                }
            } catch (e: Exception) {
                Log.w(TAG, "download failed", e)
                null
            }
            main.post { if (saved != null && !destroyed) host.onDownloadSaved(saved, name) }
        }.start()
    }

    override fun load(url: String) {
        if (!destroyed) session.loadUri(url)
    }

    override fun reload() {
        if (!destroyed) session.reload()
    }

    override fun canGoBack() = !destroyed && canBack

    override fun goBack() {
        if (!destroyed) session.goBack()
    }

    override fun onResume() {
        if (!destroyed) session.setActive(true)
    }

    override fun onPause() {
        if (!destroyed) session.setActive(false)
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        waiting.clear()
        (geckoView.parent as? android.view.ViewGroup)?.removeView(geckoView)
        geckoView.releaseSession()
        session.close()
    }

    override fun pageTool(name: String, arg: String?, result: (String?) -> Unit) {
        val p = port
        if (destroyed || p == null) {
            result(null)
            return
        }
        val call = nextCall++
        waiting[call] = result
        p.postMessage(JSONObject().put("id", call).put("fn", name).put("arg", arg ?: JSONObject.NULL))
        main.postDelayed({ waiting.remove(call)?.invoke(null) }, 3000L)
    }

    override fun clearData(everything: Boolean, done: () -> Unit) {
        val flags = if (everything) StorageController.ClearFlags.ALL
        else StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.AUTH_SESSIONS
        runtime(activity).storageController.clearData(flags).accept({ done() }, { done() })
    }

    override fun describe() = "GeckoView ${version()}"

    companion object {
        private const val TAG = "claude-phone"
        private var sharedRuntime: GeckoRuntime? = null

        fun version(): String = org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION

        /** One runtime per process, created the first time the owner selects this engine. */
        private fun runtime(ctx: Context): GeckoRuntime =
            sharedRuntime ?: GeckoRuntime.create(
                ctx.applicationContext,
                GeckoRuntimeSettings.Builder()
                    .aboutConfigEnabled(false)
                    .remoteDebuggingEnabled(false)
                    .consoleOutput(false)
                    .build()
            ).also { sharedRuntime = it }
    }
}
