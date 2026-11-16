package io.github.forkyfor.oldphone

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Applies the owner's language and theme choice to an activity. */
abstract class BaseActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrap(newBase))
    }

    protected fun noTransition() {
        if (Prefs(this).reduceMotion) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        fun wrap(base: Context): Context {
            val prefs = Prefs(base)
            val cfg = Configuration(base.resources.configuration)
            var changed = false
            if (prefs.language != "system") {
                cfg.setLocale(Locale.forLanguageTag(prefs.language))
                changed = true
            }
            val night = when (prefs.theme) {
                "dark" -> Configuration.UI_MODE_NIGHT_YES
                "light" -> Configuration.UI_MODE_NIGHT_NO
                else -> 0
            }
            if (night != 0) {
                cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                changed = true
            }
            return if (changed) base.createConfigurationContext(cfg) else base
        }

        /** BCP-47 tag used for speech recognition and text-to-speech. */
        fun speechLocale(ctx: Context): Locale {
            val l = ctx.resources.configuration.let {
                @Suppress("DEPRECATION")
                if (android.os.Build.VERSION.SDK_INT >= 24) it.locales[0] else it.locale
            }
            return l ?: Locale.getDefault()
        }
    }
}
