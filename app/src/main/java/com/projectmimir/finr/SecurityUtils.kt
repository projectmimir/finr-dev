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
        .setTitle(activity.getString(R.string.bio_title))
        .setSubtitle(activity.getString(R.string.bio_subtitle))
        .setDeviceCredentialAllowed(true)
        .build()

    prompt.authenticate(promptInfo)
}
