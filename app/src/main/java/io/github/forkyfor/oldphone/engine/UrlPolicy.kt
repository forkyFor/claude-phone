package io.github.forkyfor.oldphone.engine

/**
 * Decides where a top-level navigation goes. The embedded engine only ever shows the official
 * site and the sign-in providers it redirects to; every other link opens in the system browser.
 * Pure Kotlin so it can be unit tested on the JVM.
 */
object UrlPolicy {
    enum class Decision { INTERNAL, EXTERNAL, BLOCK }

    const val SITE_HOST = "claude.ai"
    const val HOME_URL = "https://claude.ai/"
    const val NEW_CHAT_URL = "https://claude.ai/new"
    const val PROJECTS_URL = "https://claude.ai/projects"

    /** Sign-in and bot-check hosts the official site sends the main frame or its frames to. */
    val LOGIN_HOSTS: Set<String> = setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "appleid.apple.com",
        "challenges.cloudflare.com",
    )

    private fun matches(host: String, allowed: String) = host == allowed || host.endsWith(".$allowed")

    fun isSite(host: String?): Boolean = host != null && matches(host.lowercase(), SITE_HOST)

    fun isGoogleSignIn(host: String?): Boolean = host != null && host.lowercase() == "accounts.google.com"

    fun decide(scheme: String?, host: String?, extraHosts: Set<String> = emptySet()): Decision {
        val s = scheme?.lowercase()
        val h = host?.lowercase()?.trimEnd('.')
        return when (s) {
            "https" -> when {
                h.isNullOrEmpty() -> Decision.BLOCK
                matches(h, SITE_HOST) -> Decision.INTERNAL
                LOGIN_HOSTS.any { matches(h, it) } -> Decision.INTERNAL
                extraHosts.any { it.isNotEmpty() && matches(h, it) } -> Decision.INTERNAL
                else -> Decision.EXTERNAL
            }
            // Plain http is never loaded inside the app.
            "http", "mailto", "tel" -> Decision.EXTERNAL
            "about", "blob", "data" -> Decision.INTERNAL
            else -> Decision.BLOCK // javascript:, file:, content:, intent:, custom schemes...
        }
    }

    /** A sign-in link the owner pasted: only https links on the official site are accepted. */
    fun isAcceptablePastedLink(scheme: String?, host: String?): Boolean =
        scheme?.lowercase() == "https" && isSite(host)

    /** New-chat URL with a prefilled prompt, used when text is shared to the app. */
    fun newChatWithText(encodedText: String): String = "$NEW_CHAT_URL?q=$encodedText"
}
