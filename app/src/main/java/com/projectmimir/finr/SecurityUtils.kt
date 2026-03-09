package com.projectmimir.finr

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun runBiometricPrompt(
    activity: FragmentActivity,
    onResult: (Boolean) -> Unit
) {
    // Single helper used by secure toggle enable/disable and app unlock gate.
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }

            override fun onAuthenticationFailed() {
                onResult(false)
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(AppText.BIO_TITLE)
        .setSubtitle(AppText.BIO_SUBTITLE)
        .setDeviceCredentialAllowed(true)
        .build()

    prompt.authenticate(promptInfo)
}
