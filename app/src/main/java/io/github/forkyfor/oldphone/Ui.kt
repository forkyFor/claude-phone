package io.github.forkyfor.oldphone

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Tiny helpers to build the native screens in code, with no layout library. */
object Ui {
    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    @Suppress("DEPRECATION")
    fun color(c: Context, res: Int): Int = c.resources.getColor(res)

    fun column(c: Context, padDp: Int = 24): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(c, padDp)
        setPadding(p, p, p, p)
    }

    fun scroll(c: Context, child: View): ScrollView = ScrollView(c).apply {
        isFillViewport = true
        setBackgroundColor(color(c, R.color.bg))
        addView(child, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun lp(c: Context, topDp: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(c, topDp) }

    fun title(c: Context, text: CharSequence): TextView = TextView(c).apply {
        this.text = text
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(c, R.color.on_bg))
        if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        layoutParams = lp(c, 8)
    }

    fun section(c: Context, text: CharSequence): TextView = TextView(c).apply {
        this.text = text
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = true
        setTextColor(color(c, R.color.accent))
        if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        layoutParams = lp(c, 28)
    }

    fun body(c: Context, text: CharSequence, muted: Boolean = false): TextView = TextView(c).apply {
        this.text = text
        textSize = if (muted) 15f else 17f
        setLineSpacing(0f, 1.15f)
        setTextColor(color(c, if (muted) R.color.on_bg_muted else R.color.on_bg))
        layoutParams = lp(c, 12)
    }

    private fun rounded(c: Context, fill: Int, stroke: Int = 0): RippleDrawable {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(c, 14).toFloat()
            setColor(fill)
            if (stroke != 0) setStroke(dp(c, 1), stroke)
        }
        return RippleDrawable(ColorStateList.valueOf(color(c, R.color.ripple)), shape, shape)
    }

    fun primaryButton(c: Context, text: CharSequence, onClick: () -> Unit): Button = Button(c).apply {
        this.text = text
        isAllCaps = false
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(c, R.color.on_accent))
        background = rounded(c, color(c, R.color.accent))
        stateListAnimator = null
        minimumHeight = dp(c, 60)
        setOnClickListener { onClick() }
        layoutParams = lp(c, 24)
    }

    fun secondaryButton(c: Context, text: CharSequence, onClick: () -> Unit): Button = Button(c).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        setTextColor(color(c, R.color.on_bg))
        background = rounded(c, color(c, R.color.surface), color(c, R.color.outline))
        stateListAnimator = null
        minimumHeight = dp(c, 52)
        setOnClickListener { onClick() }
        layoutParams = lp(c, 12)
    }

    fun switchRow(c: Context, label: CharSequence, checked: Boolean, onChange: (Boolean) -> Unit): Switch =
        Switch(c).apply {
            text = label
            textSize = 16f
            setTextColor(color(c, R.color.on_bg))
            isChecked = checked
            minimumHeight = dp(c, 52)
            gravity = Gravity.CENTER_VERTICAL
            setOnCheckedChangeListener { _, v -> onChange(v) }
            layoutParams = lp(c, 8)
        }
}
