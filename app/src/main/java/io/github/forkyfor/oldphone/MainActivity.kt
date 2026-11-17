package io.github.forkyfor.oldphone

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.github.forkyfor.oldphone.engine.EngineFactory
import io.github.forkyfor.oldphone.engine.EngineHost
import io.github.forkyfor.oldphone.engine.EngineInfo
import io.github.forkyfor.oldphone.engine.FileRequest
import io.github.forkyfor.oldphone.engine.LoadError
import io.github.forkyfor.oldphone.engine.UrlPolicy
import io.github.forkyfor.oldphone.engine.WebEngine
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * The whole device experience: the official site full screen, one native bar under it, and
 * native screens for everything that can go wrong. This activity is also the Home screen
 * and the assist target.
 */
class MainActivity : BaseActivity(), EngineHost, Dictation.Listener {

    private enum class Problem { NETWORK, TLS, OLD_ENGINE, NO_ENGINE, BLANK, CRASH, OTHER }

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private var engine: WebEngine? = null
    private var extraHosts: Set<String> = emptySet()

    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var webArea: TouchWatchLayout
    private lateinit var progress: ProgressBar
    private lateinit var pullHint: TextView
    private lateinit var banner: TextView
    private lateinit var bar: LinearLayout
    private lateinit var micButton: ImageButton
    private lateinit var readButton: TextView
    private lateinit var appsButton: TextView
    private lateinit var errorHolder: FrameLayout
    private lateinit var rest: RestScreen

    private lateinit var dictation: Dictation
    private lateinit var phone: PhoneActions
    private lateinit var speaker: Speaker

    private var keyboardVisible = false
    private var reading = false
    private var problem: Problem? = null
    private var oldEngineAccepted = false
    private var bannerTag: String? = null
    private var bannerAction: (() -> Unit)? = null

    private val pendingAttachments = ArrayList<Uri>()
    private var activeFileRequest: FileRequest? = null
    private var captureFile: File? = null
    private val permissionCallbacks = HashMap<Int, (Boolean) -> Unit>()
    private var nextPermissionCode = 100

    private var charging = false
    private var createdLook = ""
    private var dimmed = false
    private var crashTimes = ArrayList<Long>()

    // CI-only measurement mode, compiled out of what users do (debug builds, explicit extra).
    private var probe = false
    private var probeHttp = 0
    private var probeConsoleErrors = 0
    private var probeFirstError = ""

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        probe = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_PROBE, false)
        if (!prefs.setupDone && !probe) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            noTransition()
            return
        }
        if (probe) intent.getStringExtra(EXTRA_PROBE_ENGINE)?.let { prefs.engine = it }
        createdLook = prefs.language + "/" + prefs.theme
        dictation = Dictation(this, this)
        speaker = Speaker(this, { updateReadButton() }, { showBanner(getString(R.string.read_unavailable)) })
        phone = PhoneActions(this, phoneHost)
        buildLayout()
        setupInsets()
        extraHosts = prefs.extraHostSet()
        Thread { FilesProvider.purgeOld(applicationContext) }.start()
        startEngine(UrlPolicy.HOME_URL)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::root.isInitialized) handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!::root.isInitialized) return
        registerReceiver(powerReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
        try {
            connectivity().registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                networkCallback
            )
        } catch (e: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        if (!::root.isInitialized) return
        if (createdLook != prefs.language + "/" + prefs.theme) {
            recreate() // language or theme changed in the owner menu
            return
        }
        extraHosts = prefs.extraHostSet()
        val wanted = if (prefs.engine in EngineFactory.available) prefs.engine else "webview"
        if (engine != null && engine?.id != wanted) startEngine(UrlPolicy.HOME_URL)
        engine?.onResume()
        runPendingOwnerAction()
        readButton.visibility = if (prefs.readAloud) View.VISIBLE else View.GONE
        // In dedicated mode other apps cannot be opened at all, so the button would only mislead.
        val dedicated = Kiosk.isLocked(this) || (prefs.kiosk && Kiosk.isDeviceOwner(this))
        appsButton.visibility = if (prefs.showApps && !dedicated) View.VISIBLE else View.GONE
        applyBrightness()
        updatePower()
        hideSystemBars()
        if (prefs.kiosk && Kiosk.isDeviceOwner(this) && !Kiosk.isLocked(this)) Kiosk.enter(this)
    }

    override fun onPause() {
        super.onPause()
        if (!::root.isInitialized) return
        engine?.onPause()
        dictation.cancel()
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(restRunnable)
    }

    override fun onStop() {
        super.onStop()
        if (!::root.isInitialized) return
        try { unregisterReceiver(powerReceiver) } catch (e: Exception) { }
        try { connectivity().unregisterNetworkCallback(networkCallback) } catch (e: Exception) { }
        speaker.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!::root.isInitialized) return
        handler.removeCallbacksAndMessages(null)
        phone.dismiss()
        dictation.destroy()
        speaker.shutdown()
        activeFileRequest?.deliver(null)
        engine?.destroy()
        engine = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::root.isInitialized) hideSystemBars()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (!::root.isInitialized) return
        // While the rest screen is up, its own tap handler wakes the phone, so that the
        // waking touch is not passed on to the page underneath.
        if (!rest.showing) wake()
        scheduleIdle()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            rest.showing -> wake()
            dictation.listening -> dictation.cancel()
            engine?.canGoBack() == true -> {
                hideProblem()
                engine?.goBack()
            }
            // As the Home screen or in dedicated mode there is nowhere to go back to.
            !isDefaultHome() && !Kiosk.isLocked(this) -> moveTaskToBack(true)
        }
    }

    // ---------------------------------------------------------------- intents

    private fun handleIntent(intent: Intent) {
        wake()
        // Debug builds only: the emulator test hands over an already "recognised" sentence,
        // because CI emulators have no microphone. Release builds never read this extra.
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_PROBE, false)) {
            intent.getStringExtra(EXTRA_PROBE_SAY)?.let { said -> handler.postDelayed({ onFinal(said) }, 2500L) }
        }
        when (intent.action) {
            Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND -> handler.postDelayed({ startListening() }, 300L)
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                if (UrlPolicy.isAcceptablePastedLink(uri.scheme, uri.host)) load(uri.toString())
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> handleShare(intent)
        }
    }

    /** Text, links, images and PDFs shared from other apps go to a new chat through the page itself. */
    private fun handleShare(intent: Intent) {
        val streams = ArrayList<Uri>()
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { streams.addAll(it) }
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { streams.add(it) }
        }
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() },
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() },
        ).joinToString("\n")

        if (streams.isNotEmpty()) {
            val app = applicationContext
            Thread {
                val copied = streams.take(MAX_SHARED_FILES).mapNotNull { copyShared(app, it) }
                handler.post {
                    if (copied.isEmpty()) {
                        showBanner(getString(R.string.share_failed))
                    } else {
                        pendingAttachments.addAll(copied)
                        load(UrlPolicy.NEW_CHAT_URL)
                        showBanner(
                            resources.getQuantityString(R.plurals.attach_ready, pendingAttachments.size, pendingAttachments.size),
                            sticky = true, tag = TAG_ATTACH
                        )
                    }
                }
            }.start()
        }
        if (text.isNotBlank()) {
            if (streams.isEmpty() && text.length <= MAX_PREFILL_CHARS) {
                load(UrlPolicy.newChatWithText(Uri.encode(text)))
            } else {
                copyToClipboard(text)
                if (streams.isEmpty()) {
                    load(UrlPolicy.NEW_CHAT_URL)
                    showBanner(getString(R.string.share_text_copied), sticky = true)
                }
            }
        }
    }

    private fun copyShared(app: Context, uri: Uri): Uri? {
        return try {
            var name = "shared"
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
            }
            val out = FilesProvider.newFile(app, name)
            var total = 0L
            app.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_SHARED_BYTES) throw IllegalStateException("too large")
                        output.write(buf, 0, n)
                    }
                }
            } ?: return null
            FilesProvider.uriFor(app, out)
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- layout

    private fun buildLayout() {
        root = FrameLayout(this)
        root.setBackgroundColor(Ui.color(this, R.color.bg))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        webArea = TouchWatchLayout(this)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            isIndeterminate = false
        }
        pullHint = TextView(this).apply {
            text = getString(R.string.pull_release)
            textSize = 15f
            setTextColor(Ui.color(this@MainActivity, R.color.on_accent))
            background = pill(Ui.color(this@MainActivity, R.color.accent))
            val p = Ui.dp(this@MainActivity, 12)
            setPadding(p * 2, p, p * 2, p)
            visibility = View.GONE
        }
        webArea.addView(progress, FrameLayout.LayoutParams(MATCH, Ui.dp(this, 3), Gravity.TOP))
        webArea.addView(
            pullHint,
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Ui.dp(this@MainActivity, 24) }
        )
        webArea.onScrollIntent = { down -> setReading(down) }
        webArea.onPull = { armed, released ->
            pullHint.visibility = if (armed && !released) View.VISIBLE else View.GONE
            if (armed && released) reloadPage()
        }

        banner = TextView(this).apply {
            textSize = 16f
            setTextColor(Ui.color(this@MainActivity, R.color.on_bg))
            setBackgroundColor(Ui.color(this@MainActivity, R.color.surface))
            val p = Ui.dp(this@MainActivity, 14)
            setPadding(p, p, p, p)
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setOnClickListener {
                val action = bannerAction
                hideBanner()
                action?.invoke()
            }
        }

        bar = buildBar()
        content.addView(webArea, LinearLayout.LayoutParams(MATCH, 0, 1f))
        content.addView(banner, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))

        errorHolder = FrameLayout(this).apply { visibility = View.GONE }
        rest = RestScreen(this) { wake() }

        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(errorHolder, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(rest, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        cornerRadius = Ui.dp(this@MainActivity, 40).toFloat()
        setColor(color)
    }

    private fun barItem(icon: Int, label: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        text = getString(label)
        textSize = 12f
        gravity = Gravity.CENTER
        maxLines = 1
        setTextColor(Ui.color(this@MainActivity, R.color.on_bg))
        setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0)
        compoundDrawablePadding = Ui.dp(this@MainActivity, 2)
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
        setBackgroundResource(tv.resourceId)
        isClickable = true
        isFocusable = true
        minimumHeight = Ui.dp(this@MainActivity, 64)
        setPadding(0, Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 6))
        setOnClickListener { onClick() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBar(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Ui.color(this@MainActivity, R.color.surface))
        }
        val weight = { LinearLayout.LayoutParams(0, WRAP, 1f) }
        row.addView(barItem(R.drawable.ic_new_chat, R.string.bar_new_chat) { load(UrlPolicy.NEW_CHAT_URL) }, weight())
        row.addView(barItem(R.drawable.ic_projects, R.string.bar_projects) { load(UrlPolicy.PROJECTS_URL) }, weight())

        micButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_mic)
            setColorFilter(Ui.color(this@MainActivity, R.color.on_accent))
            background = pill(Ui.color(this@MainActivity, R.color.accent))
            contentDescription = getString(R.string.bar_mic)
            stateListAnimator = null
        }
        // Hidden owner gesture: keep the microphone pressed for three seconds.
        val ownerHold = Runnable {
            micButton.isPressed = false
            micButton.tag = "held"
            openOwnerMenu()
        }
        micButton.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.tag = null
                    handler.postDelayed(ownerHold, OWNER_HOLD_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(ownerHold)
            }
            false
        }
        micButton.setOnClickListener { if (it.tag != "held") micTapped() }
        // The same gesture for TalkBack users, as a named action on the button.
        micButton.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.action_owner_menu, getString(R.string.owner_title)))
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == R.id.action_owner_menu) {
                    openOwnerMenu()
                    return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        }
        val micSize = Ui.dp(this, 68)
        row.addView(micButton, LinearLayout.LayoutParams(micSize, micSize).apply {
            val m = Ui.dp(this@MainActivity, 6)
            setMargins(m, m, m, m)
        })

        row.addView(barItem(R.drawable.ic_camera, R.string.bar_camera) { cameraTapped() }, weight())
        readButton = barItem(R.drawable.ic_speaker, R.string.bar_read) { readTapped() }
        row.addView(readButton, weight())
        // The door to the rest of the phone: installed apps and Android settings.
        appsButton = barItem(R.drawable.ic_apps, R.string.bar_apps) {
            startActivity(Intent(this, AppsActivity::class.java))
            noTransition()
        }
        row.addView(appsButton, weight())
        return row
    }

    private fun updateReadButton() {
        val speaking = speaker.speaking
        readButton.text = getString(if (speaking) R.string.bar_stop else R.string.bar_read)
        readButton.setCompoundDrawablesWithIntrinsicBounds(0, if (speaking) R.drawable.ic_stop else R.drawable.ic_speaker, 0, 0)
    }

    private fun updateBar() {
        val show = !keyboardVisible && !reading
        if (show == (bar.visibility == View.VISIBLE)) return
        bar.visibility = if (show) View.VISIBLE else View.GONE
        if (show && !prefs.reduceMotion) {
            bar.alpha = 0f
            bar.animate().alpha(1f).setDuration(120L).start()
        }
    }

    private val readingDone = Runnable {
        reading = false
        updateBar()
    }

    /** Scrolling down the page hides the bar; scrolling up, or four quiet seconds, brings it back. */
    private fun setReading(value: Boolean) {
        handler.removeCallbacks(readingDone)
        reading = value
        if (value) handler.postDelayed(readingDone, 4000L)
        updateBar()
    }

    // ---------------------------------------------------------------- system bars, keyboard

    private fun setupInsets() {
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (Build.VERSION.SDK_INT >= 30) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { v, insets ->
                val i = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
                )
                v.setPadding(i.left, i.top, i.right, i.bottom)
                setKeyboardVisible(insets.isVisible(WindowInsets.Type.ime()))
                WindowInsets.CONSUMED
            }
        } else {
            root.fitsSystemWindows = true
            // Older Android: detect the keyboard from the visible frame, and make room for it
            // ourselves on the versions where full screen windows are not resized.
            root.viewTreeObserver.addOnGlobalLayoutListener {
                val frame = Rect()
                root.getWindowVisibleDisplayFrame(frame)
                val full = root.rootView.height
                val open = full - frame.bottom > full * 0.15
                val loc = IntArray(2)
                root.getLocationOnScreen(loc)
                val overlap = if (open) (loc[1] + root.height - root.paddingBottom - frame.bottom).coerceAtLeast(0) else 0
                val lp = content.layoutParams as FrameLayout.LayoutParams
                if (lp.bottomMargin != overlap) {
                    lp.bottomMargin = overlap
                    content.layoutParams = lp
                }
                setKeyboardVisible(open)
            }
        }
    }

    private fun setKeyboardVisible(visible: Boolean) {
        if (keyboardVisible == visible) return
        keyboardVisible = visible
        updateBar()
        if (!visible) hideSystemBars()
    }

    /** Immersive mode: status and navigation bars stay away and come back with a swipe from the edge. */
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    // ---------------------------------------------------------------- engine

    private fun startEngine(url: String) {
        engine?.destroy()
        engine = null
        val id = if (prefs.engine in EngineFactory.available) prefs.engine else "webview"
        try {
            val e = EngineFactory.create(id, this, this)
            engine = e
            webArea.addView(e.view, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        } catch (t: Throwable) {
            // Typically: the WebView package is missing, disabled or being updated.
            Log.w(TAG, "engine could not start", t)
            showProblem(Problem.NO_ENGINE, t.javaClass.simpleName)
            return
        }
        if (id == "webview" && !oldEngineAccepted && !probe) {
            val info = EngineInfo.webView(this)
            if (info.major in 1 until EngineInfo.MIN_WEBVIEW_MAJOR) {
                showProblem(Problem.OLD_ENGINE, info.toString())
                return
            }
        }
        val start = if (probe) intent.getStringExtra(EXTRA_PROBE_URL) ?: url else url
        load(start)
    }

    private fun load(url: String) {
        val uri = Uri.parse(url)
        if (UrlPolicy.decide(uri.scheme, uri.host, extraHosts) != UrlPolicy.Decision.INTERNAL) return
        val e = engine ?: return
        hideProblem()
        setReading(false)
        e.load(url)
    }

    private fun reloadPage() {
        hideProblem()
        val e = engine
        if (e == null) startEngine(UrlPolicy.HOME_URL) else if (e.currentUrl.isNullOrEmpty()) e.load(UrlPolicy.HOME_URL) else e.reload()
    }

    override fun extraHosts(): Set<String> = extraHosts

    override fun onPageStarted(url: String) {
        handler.removeCallbacks(blankCheck)
        progress.visibility = View.VISIBLE
        probeHttp = 0
        noteUrl(url)
    }

    override fun onPageFinished(url: String) {
        progress.visibility = View.GONE
        if (probe) Log.i(PROBE_TAG, JSONObject().put("event", "finished").put("http", probeHttp).toString())
        if (UrlPolicy.isSite(Uri.parse(url).host) && problem == null) {
            handler.removeCallbacks(blankCheck)
            blankChecksLeft = if (probe) 5 else 2
            handler.postDelayed(blankCheck, BLANK_CHECK_MS)
        }
    }

    override fun onUrlChanged(url: String) = noteUrl(url)

    private fun noteUrl(url: String) {
        // Google usually refuses to sign in inside embedded WebViews. We do not work around
        // that; we tell the owner what to do instead.
        if (engine?.id == "webview" && UrlPolicy.isGoogleSignIn(Uri.parse(url).host)) {
            showBanner(getString(R.string.banner_google), sticky = true, tag = TAG_GOOGLE)
        } else if (bannerTag == TAG_GOOGLE) {
            hideBanner()
        }
    }

    override fun onProgress(percent: Int) {
        progress.progress = percent
        if (percent >= 100) progress.visibility = View.GONE
    }

    override fun onMainFrameError(kind: LoadError, detail: String) {
        if (probe) Log.i(PROBE_TAG, JSONObject().put("event", "load-error").put("kind", kind.name).put("detail", detail).toString())
        showProblem(
            when (kind) {
                LoadError.NETWORK -> Problem.NETWORK
                LoadError.TLS -> Problem.TLS
                else -> Problem.OTHER
            }, detail
        )
    }

    override fun onHttpStatus(code: Int) {
        probeHttp = code
    }

    override fun onExternalLink(uri: Uri) {
        // Phone numbers in the page become a native call screen that always asks first.
        if (uri.scheme.equals("tel", ignoreCase = true)) phone.callFromLink(uri) else openExternal(uri)
    }

    override fun onEngineCrashed() {
        val now = System.currentTimeMillis()
        crashTimes.add(now)
        crashTimes = ArrayList(crashTimes.filter { now - it < 60_000L })
        handler.post {
            if (crashTimes.size >= 3) {
                engine?.destroy()
                engine = null
                showProblem(Problem.CRASH, "")
            } else {
                startEngine(UrlPolicy.HOME_URL)
            }
        }
    }

    override fun onConsoleError(message: String) {
        if (!probe) return
        probeConsoleErrors++
        if (probeFirstError.isEmpty()) probeFirstError = message.take(200)
    }

    override fun onDownload(url: String, fileName: String?) {
        val uri = Uri.parse(url)
        if (uri.scheme == "https" || uri.scheme == "http") {
            openExternal(uri)
        } else {
            showBanner(getString(R.string.download_unsupported))
        }
    }

    override fun onDownloadSaved(uri: Uri, fileName: String) {
        showBanner(getString(R.string.download_saved, fileName), sticky = true) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, contentResolver.getType(uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            } catch (e: Exception) {
                showBanner(getString(R.string.no_app_for_file))
            }
        }
    }

    private var blankChecksLeft = 0
    private val blankCheck: Runnable = object : Runnable {
        override fun run() {
            val e = engine ?: return
            e.pageTool("probe", null) { json ->
                val o = try { if (json == null) null else JSONObject(json) } catch (ex: Exception) { null }
                if (probe) {
                    Log.i(
                        PROBE_TAG, JSONObject()
                            .put("event", "page").put("api", Build.VERSION.SDK_INT).put("engine", e.describe())
                            .put("url", e.currentUrl ?: "").put("http", probeHttp)
                            .put("text", o?.optInt("text", -1) ?: -1).put("nodes", o?.optInt("nodes", -1) ?: -1)
                            .put("fields", o?.optInt("fields", -1) ?: -1).put("buttons", o?.optInt("buttons", -1) ?: -1)
                            .put("challenge", o?.optInt("challenge", -1) ?: -1)
                            .put("consoleErrors", probeConsoleErrors).put("firstError", probeFirstError).toString()
                    )
                }
                // A page that finished loading but shows no text at all is the "white page"
                // an engine that is too old produces. Explain it natively instead.
                if (probe) {
                    // Measurement runs keep sampling so slow engines and bot checks are seen over time.
                    blankChecksLeft--
                    if (blankChecksLeft > 0) handler.postDelayed(this, BLANK_CHECK_MS)
                } else if (o != null && o.optInt("outdated", 0) == 1 && problem == null && !oldEngineAccepted) {
                    // The site itself says the engine is out of date: say what to do, natively.
                    showProblem(Problem.OLD_ENGINE, "")
                } else if (o != null && o.optInt("text", 1) == 0 && problem == null) {
                    blankChecksLeft--
                    if (blankChecksLeft > 0) handler.postDelayed(this, BLANK_CHECK_MS) else showProblem(Problem.BLANK, "")
                }
            }
        }
    }

    private fun openExternal(uri: Uri) {
        if (Kiosk.isLocked(this)) {
            showBanner(getString(R.string.external_blocked, uri.host ?: uri.toString()))
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            showBanner(getString(R.string.no_browser))
        }
    }

    // ---------------------------------------------------------------- problems (native screens)

    private fun showProblem(kind: Problem, detail: String) {
        problem = kind
        handler.removeCallbacks(blankCheck)
        progress.visibility = View.GONE
        val col = Ui.column(this)
        val engineLine = engine?.describe() ?: EngineInfo.webView(this).toString()
        val (title, cause, fix) = when (kind) {
            Problem.NETWORK -> Triple(R.string.err_network_title, R.string.err_network_cause, R.string.err_network_fix)
            Problem.TLS -> Triple(R.string.err_tls_title, R.string.err_tls_cause, R.string.err_tls_fix)
            Problem.OLD_ENGINE -> Triple(R.string.err_old_title, R.string.err_old_cause, R.string.err_old_fix)
            Problem.NO_ENGINE -> Triple(R.string.err_noengine_title, R.string.err_noengine_cause, R.string.err_old_fix)
            Problem.BLANK -> Triple(R.string.err_blank_title, R.string.err_blank_cause, R.string.err_old_fix)
            Problem.CRASH -> Triple(R.string.err_crash_title, R.string.err_crash_cause, R.string.err_crash_fix)
            Problem.OTHER -> Triple(R.string.err_other_title, R.string.err_other_cause, R.string.err_network_fix)
        }
        col.addView(Ui.title(this, getString(title)))
        col.addView(Ui.body(this, getString(cause)))
        col.addView(Ui.body(this, getString(fix)))
        if (kind == Problem.OLD_ENGINE || kind == Problem.BLANK || kind == Problem.NO_ENGINE) {
            if (EngineFactory.available.size > 1) col.addView(Ui.body(this, getString(R.string.err_try_other_engine)))
            else if (Build.VERSION.SDK_INT >= 26) col.addView(Ui.body(this, getString(R.string.err_try_gecko_build)))
        }
        when (kind) {
            Problem.OLD_ENGINE -> {
                col.addView(Ui.primaryButton(this, getString(R.string.btn_update_webview)) { openWebViewUpdate() })
                col.addView(Ui.secondaryButton(this, getString(R.string.btn_update_chrome)) { openStore("com.android.chrome") })
                if (!BuildConfig.HAS_GECKO && Build.VERSION.SDK_INT >= 26) {
                    col.addView(Ui.secondaryButton(this, getString(R.string.btn_get_gecko)) {
                        requireOwner { openExternal(Uri.parse(BuildConfig.REPO_URL + "/releases/latest")) }
                    })
                }
                col.addView(Ui.secondaryButton(this, getString(R.string.btn_try_anyway)) {
                    oldEngineAccepted = true
                    hideProblem()
                    engine?.load(UrlPolicy.HOME_URL) ?: startEngine(UrlPolicy.HOME_URL)
                })
            }
            Problem.NO_ENGINE, Problem.BLANK -> {
                col.addView(Ui.primaryButton(this, getString(R.string.btn_retry)) { reloadPage() })
                col.addView(Ui.secondaryButton(this, getString(R.string.btn_update_webview)) { openWebViewUpdate() })
            }
            Problem.NETWORK -> {
                col.addView(Ui.primaryButton(this, getString(R.string.btn_retry)) { reloadPage() })
                col.addView(Ui.secondaryButton(this, getString(R.string.owner_wifi)) {
                    requireOwner { openSettings(Settings.ACTION_WIFI_SETTINGS) }
                })
            }
            Problem.TLS -> {
                col.addView(Ui.primaryButton(this, getString(R.string.btn_retry)) { reloadPage() })
                col.addView(Ui.secondaryButton(this, getString(R.string.btn_date_settings)) {
                    requireOwner { openSettings(Settings.ACTION_DATE_SETTINGS) }
                })
            }
            else -> col.addView(Ui.primaryButton(this, getString(R.string.btn_retry)) { reloadPage() })
        }
        col.addView(Ui.secondaryButton(this, getString(R.string.owner_title)) { openOwnerMenu() })
        val facts = "$engineLine · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
            (if (detail.isNotBlank()) "\n$detail" else "")
        col.addView(Ui.body(this, facts, muted = true))
        errorHolder.removeAllViews()
        errorHolder.addView(Ui.scroll(this, col), FrameLayout.LayoutParams(MATCH, MATCH))
        errorHolder.visibility = View.VISIBLE
        content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun hideProblem() {
        if (problem == null) return
        problem = null
        errorHolder.visibility = View.GONE
        errorHolder.removeAllViews()
        content.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    private fun openWebViewUpdate() = openStore(EngineInfo.webView(this).packageName ?: "com.google.android.webview")

    private fun openStore(pkg: String) {
        requireOwner {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                showBanner(getString(R.string.no_store))
            }
        }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) { }
        }
    }

    // ---------------------------------------------------------------- banner

    private val bannerTimeout = Runnable { hideBanner() }

    private fun showBanner(text: CharSequence, sticky: Boolean = false, tag: String? = null, action: (() -> Unit)? = null) {
        handler.removeCallbacks(bannerTimeout)
        banner.text = text
        bannerTag = tag
        bannerAction = action
        banner.visibility = View.VISIBLE
        if (!sticky) handler.postDelayed(bannerTimeout, 7000L)
    }

    private fun hideBanner() {
        handler.removeCallbacks(bannerTimeout)
        banner.visibility = View.GONE
        bannerTag = null
        bannerAction = null
    }

    private fun copyToClipboard(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", text))
    }

    // ---------------------------------------------------------------- permissions

    private fun withPermissions(names: List<String>, result: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 23) {
            result(true)
            return
        }
        val missing = names.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            result(true)
            return
        }
        val code = nextPermissionCode++
        permissionCallbacks[code] = result
        requestPermissions(missing.toTypedArray(), code)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val cb = permissionCallbacks.remove(requestCode) ?: return
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (!granted) {
            val message = when {
                permissions.contains(Manifest.permission.RECORD_AUDIO) -> R.string.perm_mic_denied
                permissions.contains(Manifest.permission.CAMERA) -> R.string.perm_camera_denied
                permissions.contains(Manifest.permission.READ_CONTACTS) -> R.string.phone_contacts_denied
                else -> R.string.perm_phone_denied
            }
            showBanner(getString(message), sticky = true) {
                requireOwner {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
        }
        cb(granted)
    }

    override fun onMediaRequest(audio: Boolean, video: Boolean, result: (Boolean) -> Unit) {
        val names = ArrayList<String>()
        if (audio) names.add(Manifest.permission.RECORD_AUDIO)
        if (video) names.add(Manifest.permission.CAMERA)
        withPermissions(names, result)
    }

    // ---------------------------------------------------------------- attachments

    override fun onFileRequest(request: FileRequest) {
        activeFileRequest?.deliver(null)
        activeFileRequest = null
        if (pendingAttachments.isNotEmpty()) {
            // A photo or shared file is waiting: give it to the page's own attach button.
            val files = if (request.multiple) ArrayList(pendingAttachments) else listOf(pendingAttachments[0])
            pendingAttachments.removeAll(files.toSet())
            if (pendingAttachments.isEmpty() && bannerTag == TAG_ATTACH) hideBanner()
            request.deliver(files)
            return
        }
        activeFileRequest = request
        val labels = arrayOf(getString(R.string.attach_camera), getString(R.string.attach_photos), getString(R.string.attach_files))
        var chosen = false
        AlertDialog.Builder(this)
            .setTitle(R.string.attach_title)
            .setItems(labels) { _, which ->
                chosen = true
                when (which) {
                    0 -> launchCamera()
                    1 -> launchPicker("image/*", request.multiple)
                    else -> launchPicker("*/*", request.multiple)
                }
            }
            .setOnDismissListener { if (!chosen) finishFileRequest(null) }
            .show()
    }

    private fun finishFileRequest(uris: List<Uri>?) {
        val r = activeFileRequest
        activeFileRequest = null
        r?.deliver(uris)
    }

    private fun cameraTapped() {
        activeFileRequest = null
        launchCamera()
    }

    private fun launchCamera() {
        withPermissions(listOf(Manifest.permission.CAMERA)) { granted ->
            if (!granted) {
                finishFileRequest(null)
                return@withPermissions
            }
            val file = FilesProvider.newFile(this, "photo.jpg")
            val uri = FilesProvider.uriFor(this, file)
            captureFile = file
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("photo", uri)
            try {
                startActivityForResult(intent, RC_CAPTURE)
            } catch (e: ActivityNotFoundException) {
                showBanner(getString(R.string.no_camera_app))
                finishFileRequest(null)
            } catch (e: SecurityException) {
                showBanner(getString(R.string.no_camera_app))
                finishFileRequest(null)
            }
        }
    }

    private fun launchPicker(mime: String, multiple: Boolean) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        try {
            startActivityForResult(intent, RC_PICK)
        } catch (e: Exception) {
            showBanner(getString(R.string.no_app_for_file))
            finishFileRequest(null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RC_CAPTURE -> {
                val file = captureFile
                captureFile = null
                val ok = resultCode == RESULT_OK && file != null && file.length() > 0
                val uri = if (ok) FilesProvider.uriFor(this, file) else null
                if (!ok) file?.delete()
                if (activeFileRequest != null) {
                    finishFileRequest(uri?.let { listOf(it) })
                } else if (uri != null) {
                    // Camera button on the bar: keep the photo until the owner taps attach in the page.
                    pendingAttachments.add(uri)
                    showBanner(
                        resources.getQuantityString(R.plurals.attach_ready, pendingAttachments.size, pendingAttachments.size),
                        sticky = true, tag = TAG_ATTACH
                    )
                }
            }
            RC_PICK -> {
                val uris = ArrayList<Uri>()
                if (resultCode == RESULT_OK && data != null) {
                    val clip = data.clipData
                    if (clip != null) for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                    else data.data?.let { uris.add(it) }
                }
                finishFileRequest(uris.ifEmpty { null })
            }
        }
    }

    // ---------------------------------------------------------------- voice

    private fun micTapped() {
        if (speaker.speaking) speaker.stop()
        if (dictation.listening) dictation.cancel() else startListening()
    }

    private fun startListening() {
        if (!dictation.available()) {
            AlertDialog.Builder(this).setTitle(R.string.voice_unavailable_title)
                .setMessage(R.string.voice_unavailable).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        withPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { granted ->
            if (granted) dictation.start(speechLocale(this))
        }
    }

    override fun onListening(active: Boolean) {
        micButton.background = pill(Ui.color(this, if (active) R.color.danger else R.color.accent))
        micButton.contentDescription = getString(if (active) R.string.bar_mic_stop else R.string.bar_mic)
        if (active) showBanner(getString(R.string.voice_listening), sticky = true, tag = TAG_VOICE)
        else if (bannerTag == TAG_VOICE) hideBanner()
    }

    override fun onPartial(text: String) {
        if (bannerTag == TAG_VOICE) banner.text = text
    }

    override fun onFinal(text: String) {
        // A "yes" / "no" for a call waiting for confirmation comes first.
        if (phone.handleAnswer(text)) return
        when (VoiceCommands.match(text)) {
            VoiceCommand.NEW_CHAT -> load(UrlPolicy.NEW_CHAT_URL)
            VoiceCommand.PROJECTS -> load(UrlPolicy.PROJECTS_URL)
            VoiceCommand.BACK -> if (engine?.canGoBack() == true) engine?.goBack()
            VoiceCommand.STOP -> speaker.stop()
            VoiceCommand.READ -> readLastReply()
            null -> {
                val command = if (prefs.phoneCommands) PhoneCommands.parse(text) else null
                if (command != null) phone.run(command, text) else typeIntoPage(text)
            }
        }
    }

    /** What the phone commands may use from this screen. */
    private val phoneHost = object : PhoneActions.Host {
        override fun permissions(names: List<String>, result: (Boolean) -> Unit) = withPermissions(names, result)
        override fun banner(text: String, action: (() -> Unit)?) = showBanner(text, sticky = action != null, action = action)
        override fun say(text: String) = speaker.speak(text, speechLocale(this@MainActivity))
        override fun lastReply(result: (String?) -> Unit) {
            val e = engine
            if (e == null) result(null) else e.pageTool("lastReply", null, result)
        }
        override fun typeIntoPage(text: String) = this@MainActivity.typeIntoPage(text)
        override fun ownerGate(then: () -> Unit) {
            if (prefs.showApps && !dedicated()) then() else requireOwner(then)
        }
        override fun dedicated(): Boolean = Kiosk.isLocked(this@MainActivity) || (prefs.kiosk && Kiosk.isDeviceOwner(this@MainActivity))
    }

    override fun onError(code: Int) {
        val msg = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_no_match
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> R.string.voice_network
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.perm_mic_denied
            else -> R.string.voice_error
        }
        showBanner(getString(msg))
    }

    /** Dictation helper: fragile, optional, and falls back to the clipboard. */
    private fun typeIntoPage(text: String) {
        val e = engine
        if (!prefs.dictation || e == null || problem != null) {
            copyToClipboard(text)
            showBanner(getString(R.string.dictation_copied, text), sticky = true)
            return
        }
        e.pageTool("insertText", text) { r ->
            if (r != "ok") {
                copyToClipboard(text)
                showBanner(getString(R.string.dictation_copied, text), sticky = true)
            }
        }
    }

    private fun readTapped() {
        if (speaker.speaking) speaker.stop() else readLastReply()
    }

    private fun readLastReply() {
        if (!prefs.readAloud) {
            showBanner(getString(R.string.read_disabled))
            return
        }
        engine?.pageTool("lastReply", null) { text ->
            if (text.isNullOrBlank()) showBanner(getString(R.string.read_nothing), sticky = true)
            else speaker.speak(text, speechLocale(this))
        }
    }

    // ---------------------------------------------------------------- owner menu

    private fun requireOwner(then: () -> Unit) = PinPrompt.ask(this, then)

    private fun openOwnerMenu() {
        requireOwner {
            OwnerActivity.engineDescription = engine?.describe()
            startActivity(Intent(this, OwnerActivity::class.java))
            noTransition()
        }
    }

    private fun runPendingOwnerAction() {
        val action = pendingOwnerAction ?: return
        pendingOwnerAction = null
        when {
            action == ACTION_RELOAD -> reloadPage()
            action == ACTION_SIGN_OUT -> engine?.clearData(false) { load(UrlPolicy.HOME_URL) }
            action == ACTION_CLEAR_ALL -> engine?.clearData(true) { load(UrlPolicy.HOME_URL) }
            action == ACTION_BROWSER -> openExternal(Uri.parse(engine?.currentUrl ?: UrlPolicy.HOME_URL))
            action == ACTION_PIN_SCREEN -> try { startLockTask() } catch (e: Exception) { }
            action == ACTION_UNLOCK -> Kiosk.exit(this)
            action == ACTION_REMOVE_OWNER -> {
                val ok = Kiosk.removeDeviceOwner(this)
                showBanner(getString(if (ok) R.string.kiosk_removed else R.string.kiosk_remove_failed), sticky = true)
            }
            action.startsWith(ACTION_LOAD) -> load(action.removePrefix(ACTION_LOAD))
        }
    }

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    // ---------------------------------------------------------------- always-on behaviour

    private fun connectivity() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** When the network comes back, a failed page is reloaded without anyone touching the phone. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.postDelayed({
                if (problem == Problem.NETWORK || problem == Problem.OTHER) reloadPage()
            }, 1500L)
        }
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updatePower()
    }

    private fun updatePower() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        // The screen is only kept on while the phone is plugged in.
        if (alwaysOn()) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scheduleIdle()
    }

    private fun alwaysOn() = prefs.keepScreenOn && charging && !probe

    private fun busy() = dictation.listening || speaker.speaking

    private val dimRunnable: Runnable = object : Runnable {
        override fun run() {
            if (busy()) {
                handler.postDelayed(this, DIM_AFTER_MS)
                return
            }
            dimmed = true
            setWindowBrightness(0.02f)
        }
    }

    private val restRunnable: Runnable = object : Runnable {
        override fun run() {
            if (busy()) {
                handler.postDelayed(this, REST_AFTER_MS)
                return
            }
            dimmed = true
            setWindowBrightness(0.01f)
            rest.show()
        }
    }

    private fun scheduleIdle() {
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(restRunnable)
        if (!alwaysOn()) return
        handler.postDelayed(dimRunnable, DIM_AFTER_MS)
        if (prefs.restScreen) handler.postDelayed(restRunnable, REST_AFTER_MS)
    }

    private fun wake() {
        if (!dimmed && !rest.showing) return
        dimmed = false
        rest.hide()
        applyBrightness()
    }

    private fun applyBrightness() {
        val b = prefs.brightness
        setWindowBrightness(if (b < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else b.coerceIn(5, 100) / 100f)
    }

    private fun setWindowBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value
        window.attributes = lp
    }

    // ---------------------------------------------------------------- touch watching

    /**
     * Wraps the engine view and only observes touches (it never takes them from the page):
     * vertical drags tell us the owner is reading, and a long pull down that starts in the
     * top strip reloads the page. A classic pull-to-refresh cannot work here because the
     * site scrolls an inner panel, not the document.
     */
    class TouchWatchLayout(ctx: Context) : FrameLayout(ctx) {
        var onScrollIntent: ((down: Boolean) -> Unit)? = null
        var onPull: ((armed: Boolean, released: Boolean) -> Unit)? = null
        private var startX = 0f
        private var startY = 0f
        private var lastY = 0f
        private var pullCandidate = false
        private var armed = false
        private val slop = Ui.dp(ctx, 24)
        private val strip = Ui.dp(ctx, 72)
        private val pullDistance = Ui.dp(ctx, 150)

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x
                    startY = ev.y
                    lastY = ev.y
                    pullCandidate = ev.y < strip
                    armed = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> pullCandidate = false
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.y - lastY
                    if (abs(dy) > slop) {
                        onScrollIntent?.invoke(dy < 0)
                        lastY = ev.y
                    }
                    if (pullCandidate) {
                        val total = ev.y - startY
                        val now = total > pullDistance && abs(ev.x - startX) < total / 2
                        if (now != armed) {
                            armed = now
                            onPull?.invoke(armed, false)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (armed) onPull?.invoke(ev.actionMasked == MotionEvent.ACTION_UP, true)
                    armed = false
                    pullCandidate = false
                }
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    companion object {
        private const val TAG = "OldPhoneClaude"
        const val PROBE_TAG = "OPC_PROBE"
        const val EXTRA_PROBE = "probe"
        const val EXTRA_PROBE_URL = "probe_url"
        const val EXTRA_PROBE_ENGINE = "probe_engine"
        const val EXTRA_PROBE_SAY = "probe_say"

        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val RC_CAPTURE = 1
        private const val RC_PICK = 2
        private const val TAG_ATTACH = "attach"
        private const val TAG_VOICE = "voice"
        private const val TAG_GOOGLE = "google"

        private const val OWNER_HOLD_MS = 3000L
        private const val BLANK_CHECK_MS = 12_000L
        private const val DIM_AFTER_MS = 60_000L
        private const val REST_AFTER_MS = 180_000L
        private const val MAX_PREFILL_CHARS = 3000
        private const val MAX_SHARED_FILES = 5
        private const val MAX_SHARED_BYTES = 30L * 1024 * 1024

        // Requests left by the owner menu, executed when this activity is back in front.
        const val ACTION_RELOAD = "reload"
        const val ACTION_SIGN_OUT = "sign_out"
        const val ACTION_CLEAR_ALL = "clear_all"
        const val ACTION_BROWSER = "browser"
        const val ACTION_PIN_SCREEN = "pin_screen"
        const val ACTION_UNLOCK = "unlock"
        const val ACTION_REMOVE_OWNER = "remove_owner"
        const val ACTION_LOAD = "load:"
        var pendingOwnerAction: String? = null
    }
}
