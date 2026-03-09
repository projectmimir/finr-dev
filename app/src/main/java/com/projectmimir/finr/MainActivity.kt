package com.projectmimir.finr

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedTextFromIntent = extractSharedText(intent)

        setContent {
            val context = this@MainActivity
            val prefs = remember { context.getSharedPreferences(AppText.PREFS_NAME, Context.MODE_PRIVATE) }
            val initialThemeMode = remember {
                val prefMode = prefs.getString(AppText.PREF_THEME_MODE, null)
                if (prefMode != null) {
                    ThemeMode.fromPref(prefMode)
                } else if (prefs.getBoolean(AppText.PREF_THEME_DARK, false)) {
                    ThemeMode.DARK
                } else {
                    ThemeMode.LIGHT
                }
            }
            var themeMode by remember { mutableStateOf(initialThemeMode) }
            SmsReaderTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // App-wide state holders used by the top-level screen router.
                    val db = remember { AppDatabase.getInstance(context) }
                    val scope = rememberCoroutineScope()
                    var hasPermission by remember { mutableStateOf(false) }
                    var isLoading by remember { mutableStateOf(true) }
                    var showSplash by remember { mutableStateOf(true) }
                    var pendingSharedText by remember { mutableStateOf(sharedTextFromIntent) }
                    var secureEnabled by remember { mutableStateOf(prefs.getBoolean(AppText.PREF_SECURE_ENABLED, false)) }
                    var isUnlocked by remember { mutableStateOf(!secureEnabled) }

                    val transactions by db.transactionDao()
                        .getAll()
                        .collectAsStateWithLifecycle(emptyList())
                    val categories by db.categoryDao()
                        .getAll()
                        .collectAsStateWithLifecycle(emptyList())

                    // Permission result drives the initial sync + main UI unlock.
                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { grants ->
                        val readGranted = grants[Manifest.permission.READ_SMS] == true
                        val receiveGranted = grants[Manifest.permission.RECEIVE_SMS] == true
                        hasPermission = readGranted && receiveGranted
                        if (hasPermission) {
                            scope.launch {
                                seedCategories(db)
                                syncSmsToDb(context, db)
                                if (!prefs.getBoolean(AppText.PREF_BANK_BACKFILL_DONE, false)) {
                                    backfillBankColumns(db)
                                    prefs.edit().putBoolean(AppText.PREF_BANK_BACKFILL_DONE, true).apply()
                                }
                                isLoading = false
                            }
                        } else {
                            isLoading = false
                        }
                    }

                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { /* no-op */ }

                    // One-time startup flow:
                    // 1) Seed category master data
                    // 2) Check SMS permission
                    // 3) Incrementally sync SMS into DB
                    // 4) Trigger app lock if secure mode is enabled
                    LaunchedEffect(Unit) {
                        delay(2000)
                        showSplash = false

                        // Always seed category master data before transaction writes.
                        // This avoids FK crashes when DB is recreated but prefs still mark seeding as done.
                        seedCategories(db)
                        prefs.edit().putBoolean(AppText.PREF_CATEGORIES_SEEDED, true).apply()
                        hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_SMS
                        ) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECEIVE_SMS
                            ) == PackageManager.PERMISSION_GRANTED

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasNotifPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasNotifPermission) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }

                        // Keep daily 22:30 summary notification active.
                        DailySummaryScheduler.schedule(context)

                        if (hasPermission) {
                            syncSmsToDb(context, db)
                        }
                        if (!prefs.getBoolean(AppText.PREF_BANK_BACKFILL_DONE, false)) {
                            backfillBankColumns(db)
                            prefs.edit().putBoolean(AppText.PREF_BANK_BACKFILL_DONE, true).apply()
                        }
                        if (secureEnabled && !isUnlocked) {
                            runBiometricPrompt(this@MainActivity) { success ->
                                if (success) {
                                    isUnlocked = true
                                }
                            }
                        }
                        isLoading = false
                    }

                    // Route to the correct top-level surface based on current app state.
                    if (showSplash) {
                        SplashScreen()
                    } else if (!hasPermission) {
                        PermissionScreen(onGrant = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.RECEIVE_SMS
                                )
                            )
                        })
                    } else if (isLoading) {
                        LoadingScreen()
                    } else if (secureEnabled && !isUnlocked) {
                        SecureGateScreen(
                            onUnlock = {
                                runBiometricPrompt(this@MainActivity) { success ->
                                    if (success) {
                                        isUnlocked = true
                                    }
                                }
                            }
                        )
                    } else {
                        SmsListScreen(
                            transactions = transactions,
                            categories = categories,
                            onUpdate = { scope.launch { db.transactionDao().update(it) } },
                            onDelete = { scope.launch { db.transactionDao().delete(it) } },
                            onAdd = { scope.launch { db.transactionDao().upsertAll(listOf(it)) } },
                            onRefresh = { onDone ->
                                scope.launch {
                                    val added = syncSmsToDb(context, db)
                                    onDone(added)
                                }
                            },
                            onRecycle = {
                                scope.launch {
                                    isLoading = true
                                    recycleTransactions(context, db)
                                    isLoading = false
                                }
                            },
                            secureEnabled = secureEnabled,
                            onEnableSecure = {
                                secureEnabled = true
                                isUnlocked = true
                                prefs.edit().putBoolean(AppText.PREF_SECURE_ENABLED, true).apply()
                            },
                            onDisableSecure = {
                                secureEnabled = false
                                isUnlocked = true
                                prefs.edit().putBoolean(AppText.PREF_SECURE_ENABLED, false).apply()
                            },
                            initialSharedText = pendingSharedText,
                            onSharedTextHandled = { pendingSharedText = null },
                            onSharedTextFailureExit = { finish() },
                            themeMode = themeMode,
                            onThemeChanged = { mode ->
                                themeMode = mode
                                prefs.edit()
                                    .putString(AppText.PREF_THEME_MODE, mode.prefValue)
                                    .putBoolean(AppText.PREF_THEME_DARK, mode != ThemeMode.LIGHT)
                                    .apply()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("text/")) return null
        return intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() }
    }
}
