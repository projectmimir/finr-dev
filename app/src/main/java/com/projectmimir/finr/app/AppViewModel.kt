package com.projectmimir.finr

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val themeMode: ThemeMode,
    val showSplash: Boolean = true,
    val hasPermission: Boolean = false,
    val isLoading: Boolean = true,
    val secureEnabled: Boolean = false,
    val isUnlocked: Boolean = true,
    val pendingSharedText: String? = null,
    val isRefreshing: Boolean = false
)

class AppViewModel(
    application: Application,
    initialSharedText: String?
) : ViewModel() {
    private val appContext = application.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val transactionsRepository = TransactionsRepository(appContext)

    private val _uiState = MutableStateFlow(
        AppUiState(
            themeMode = settingsRepository.themeMode(),
            secureEnabled = settingsRepository.secureEnabled(),
            isUnlocked = !settingsRepository.secureEnabled(),
            pendingSharedText = initialSharedText
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    val transactions: Flow<List<TransactionEntity>> = transactionsRepository.transactions
    val categories: Flow<List<CategoryEntity>> = transactionsRepository.categories

    private var startupStarted = false

    fun start() {
        if (startupStarted) return
        startupStarted = true
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(showSplash = false) }

            transactionsRepository.initialize()
            settingsRepository.setCategoriesSeeded(true)

            val hasPermission = hasSmsPermissions()
            _uiState.update { it.copy(hasPermission = hasPermission) }

            if (hasPermission) {
                transactionsRepository.syncSms()
                maybeRunBankBackfill()
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun shouldRequestNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    }

    fun onSmsPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted, isLoading = granted) }
        if (!granted) return

        viewModelScope.launch {
            transactionsRepository.initialize()
            settingsRepository.setCategoriesSeeded(true)
            transactionsRepository.syncSms()
            maybeRunBankBackfill()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun enableSecureMode() {
        settingsRepository.setSecureEnabled(true)
        _uiState.update { it.copy(secureEnabled = true, isUnlocked = true) }
    }

    fun disableSecureMode() {
        settingsRepository.setSecureEnabled(false)
        _uiState.update { it.copy(secureEnabled = false, isUnlocked = true) }
    }

    fun unlock() {
        _uiState.update { it.copy(isUnlocked = true) }
    }

    fun clearSharedText() {
        _uiState.update { it.copy(pendingSharedText = null) }
    }

    fun refreshTransactions(onResult: (Int) -> Unit = {}) {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val added = transactionsRepository.syncSms()
            _uiState.update { it.copy(isRefreshing = false) }
            onResult(added)
        }
    }

    fun recycleTransactions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            transactionsRepository.recycleTransactions()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionsRepository.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionsRepository.deleteTransaction(transaction)
        }
    }

    fun addTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionsRepository.addTransaction(transaction)
        }
    }

    private suspend fun maybeRunBankBackfill() {
        if (settingsRepository.bankBackfillDone()) return
        transactionsRepository.backfillMissingBankData()
        settingsRepository.setBankBackfillDone(true)
    }

    private fun hasSmsPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        fun factory(
            application: Application,
            initialSharedText: String?
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(application, initialSharedText) as T
                }
            }
        }
    }
}
