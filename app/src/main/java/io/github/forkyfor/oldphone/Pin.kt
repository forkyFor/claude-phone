package io.github.forkyfor.oldphone

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Owner PIN. There is no default PIN: the owner chooses one during setup.
 * Only a salted PBKDF2 hash is stored. Wrong attempts are slowed down, never wiped.
 */
object Pin {
    private const val ITERATIONS = 20000
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12
    private const val FREE_ATTEMPTS = 5

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        // HmacSHA1 is the only PBKDF2 variant available on every supported Android version.
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun isValidFormat(pin: String) = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    fun isSet(ctx: Context) = Prefs(ctx).sp.contains("pin_hash")

    fun set(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        Prefs(ctx).sp.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", hash(pin, salt))
            .putInt("pin_fails", 0).putLong("pin_lock_until", 0L)
            .apply()
    }

    /** Milliseconds the owner still has to wait before the next attempt (0 = can try now). */
    fun lockedForMs(ctx: Context): Long =
        (Prefs(ctx).sp.getLong("pin_lock_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun check(ctx: Context, pin: String): Boolean {
        val sp = Prefs(ctx).sp
        if (lockedForMs(ctx) > 0) return false
        val salt = Base64.decode(sp.getString("pin_salt", "") ?: "", Base64.NO_WRAP)
        val ok = salt.isNotEmpty() && hash(pin, salt) == sp.getString("pin_hash", null)
        if (ok) {
            sp.edit().putInt("pin_fails", 0).putLong("pin_lock_until", 0L).apply()
        } else {
            val fails = sp.getInt("pin_fails", 0) + 1
            val e = sp.edit().putInt("pin_fails", fails)
            if (fails >= FREE_ATTEMPTS) e.putLong("pin_lock_until", System.currentTimeMillis() + delayFor(fails))
            e.apply()
        }
        return ok
    }

    /** 30 s after the 5th wrong attempt, doubling up to 15 minutes. */
    fun delayFor(fails: Int): Long {
        if (fails < FREE_ATTEMPTS) return 0L
        val shift = (fails - FREE_ATTEMPTS).coerceAtMost(5)
        return (30_000L shl shift).coerceAtMost(15 * 60_000L)
    }
}
