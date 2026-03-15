package com.projectmimir.finr

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FinrApp(
    context: Context,
    initialSharedText: String?,
    onSharedTextFailureExit: () -> Unit
) {
    val appViewModel: AppViewModel = viewModel(
        factory = AppViewModel.factory(context.applicationContext as Application, initialSharedText)
    )
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
    val transactions by appViewModel.transactions.collectAsStateWithLifecycle(emptyList())
    val categories by appViewModel.categories.collectAsStateWithLifecycle(emptyList())

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val readGranted = grants[Manifest.permission.READ_SMS] == true
        val receiveGranted = grants[Manifest.permission.RECEIVE_SMS] == true
        appViewModel.onSmsPermissionResult(readGranted && receiveGranted)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    LaunchedEffect(Unit) {
        DailySummaryScheduler.schedule(context)
        if (appViewModel.shouldRequestNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        appViewModel.start()
    }

    SmsReaderTheme(themeMode = uiState.themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                uiState.showSplash -> SplashScreen()
                !uiState.hasPermission -> {
                    PermissionScreen(
                        onGrant = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.RECEIVE_SMS
                                )
                            )
                        }
                    )
                }
                uiState.isLoading -> LoadingScreen()
                uiState.secureEnabled && !uiState.isUnlocked -> {
                    SecureGateScreen(
                        onUnlock = {
                            runBiometricPrompt(context as androidx.fragment.app.FragmentActivity) { success ->
                                if (success) {
                                    appViewModel.unlock()
                                }
                            }
                        }
                    )
                }
                else -> {
                    SmsListScreen(
                        transactions = transactions,
                        categories = categories,
                        onUpdate = appViewModel::updateTransaction,
                        onDelete = appViewModel::deleteTransaction,
                        onAdd = appViewModel::addTransaction,
                        onRefresh = appViewModel::refreshTransactions,
                        onRecycle = appViewModel::recycleTransactions,
                        secureEnabled = uiState.secureEnabled,
                        onEnableSecure = appViewModel::enableSecureMode,
                        onDisableSecure = appViewModel::disableSecureMode,
                        initialSharedText = uiState.pendingSharedText,
                        onSharedTextHandled = appViewModel::clearSharedText,
                        onSharedTextFailureExit = onSharedTextFailureExit,
                        themeMode = uiState.themeMode,
                        onThemeChanged = appViewModel::setThemeMode,
                        isRefreshing = uiState.isRefreshing
                    )
                }
            }
        }
    }
}
