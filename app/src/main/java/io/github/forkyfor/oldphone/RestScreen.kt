package io.github.forkyfor.oldphone

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.random.Random

/**
 * Dark rest screen for always-on use: pure black with a dim clock that moves a little every
 * minute, so OLED panels do not burn in. Any touch wakes it.
 */
class RestScreen(ctx: Context, private val onWake: () -> Unit) : FrameLayout(ctx) {
    private val handler = Handler(Looper.getMainLooper())
    private val box = LinearLayout(ctx)
    private val mover = object : Runnable {
        override fun run() {
            move()
            handler.postDelayed(this, 60_000L)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        isClickable = true
        isFocusable = true
        contentDescription = ctx.getString(R.string.rest_hint)
        setOnClickListener { onWake() }
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        val clock = TextClock(ctx).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 64f
            setTextColor(0xFF8A8A8A.toInt())
        }
        val hint = TextView(ctx).apply {
            text = ctx.getString(R.string.rest_hint)
            textSize = 15f
            setTextColor(0xFF5E5E5E.toInt())
        }
        box.addView(clock)
        box.addView(hint)
        addView(box, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        visibility = View.GONE
    }

    val showing get() = visibility == View.VISIBLE

    fun show() {
        if (showing) return
        visibility = View.VISIBLE
        handler.post(mover)
    }

    fun hide() {
        handler.removeCallbacks(mover)
        visibility = View.GONE
    }

    private fun move() {
        val maxX = ((width - box.width) / 2 - Ui.dp(context, 16)).coerceAtLeast(0)
        val maxY = ((height - box.height) / 2 - Ui.dp(context, 16)).coerceAtLeast(0)
        box.translationX = if (maxX == 0) 0f else Random.nextInt(-maxX, maxX + 1).toFloat()
        box.translationY = if (maxY == 0) 0f else Random.nextInt(-maxY, maxY + 1).toFloat()
    }
}
