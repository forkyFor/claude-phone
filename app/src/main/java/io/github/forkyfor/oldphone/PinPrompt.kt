package io.github.forkyfor.oldphone

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout

/** PIN dialogs guarding the owner menu. */
object PinPrompt {
    private fun field(a: Activity, hint: Int) = EditText(a).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        setHint(hint)
        textSize = 22f
    }

    private fun box(a: Activity, vararg fields: EditText) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        val p = Ui.dp(a, 24)
        setPadding(p, p / 2, p, 0)
        fields.forEach { addView(it) }
    }

    /** Runs [then] after the right PIN. If no PIN exists yet, the owner chooses one first. */
    fun ask(a: Activity, then: () -> Unit) {
        if (!Pin.isSet(a)) {
            create(a, then)
            return
        }
        val input = field(a, R.string.pin_hint)
        val dialog = AlertDialog.Builder(a)
            .setTitle(R.string.pin_title)
            .setView(box(a, input))
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val wait = Pin.lockedForMs(a)
            when {
                wait > 0 -> input.error = a.getString(R.string.pin_wait, (wait / 1000 + 1).toInt())
                Pin.check(a, input.text.toString()) -> {
                    dialog.dismiss()
                    then()
                }
                else -> {
                    input.setText("")
                    val again = Pin.lockedForMs(a)
                    input.error = if (again > 0) a.getString(R.string.pin_wait, (again / 1000 + 1).toInt())
                    else a.getString(R.string.pin_wrong)
                }
            }
        }
    }

    /** Lets the owner choose (or change) the PIN. There is never a default one. */
    fun create(a: Activity, then: () -> Unit) {
        val first = field(a, R.string.pin_new_hint)
        val second = field(a, R.string.pin_repeat_hint)
        val dialog = AlertDialog.Builder(a)
            .setTitle(R.string.pin_create_title)
            .setMessage(R.string.pin_create_body)
            .setView(box(a, first, second))
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val pin = first.text.toString()
            when {
                !Pin.isValidFormat(pin) -> first.error = a.getString(R.string.pin_format)
                pin != second.text.toString() -> second.error = a.getString(R.string.pin_mismatch)
                else -> {
                    Pin.set(a, pin)
                    dialog.dismiss()
                    then()
                }
            }
        }
    }
}
