package io.github.akrishna87.jotter

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import java.security.MessageDigest

/** The passcode lock: a hash of the code is kept (never the code), and a fingerprint can open it too. */
object Lock {
    fun hash(code: String): String =
        MessageDigest.getInstance("SHA-256").digest(("jotter:" + code).toByteArray()).joinToString("") { "%02x".format(it) }

    fun matches(code: String, hash: String?): Boolean = hash != null && hash(code) == hash

    /** When the app last went to the background, so it can lock again after a while. */
    @Volatile var leftAt: Long = 0L

    @Volatile var unlocked: Boolean = false

    fun needsUnlock(passcode: String?, lockAfterMinutes: Int, now: Long = System.currentTimeMillis()): Boolean =
        passcode != null && (!unlocked || (leftAt != 0L && now - leftAt >= lockAfterMinutes * 60_000L))

    fun canUseFingerprint(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) &&
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)
        ) return false
        val m = context.getSystemService(BiometricManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return m.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Asks for a fingerprint (or face); calls [onSuccess] if it's accepted. */
    fun fingerprint(context: Context, onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT < 29) return
        val prompt = BiometricPrompt.Builder(context)
            .setTitle("Unlock Jotter")
            .setNegativeButton("Use passcode", context.mainExecutor) { _, _ -> }
            .build()
        prompt.authenticate(CancellationSignal(), context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
        })
    }
}
