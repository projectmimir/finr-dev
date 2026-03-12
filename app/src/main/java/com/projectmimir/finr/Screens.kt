package com.projectmimir.finr

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.launch

private data class PickerConfig(
    val title: String,
    val options: List<String>,
    val onSelect: (String) -> Unit
)

@Composable
private fun ConfigureLightStatusBarIcons() {
    val view = LocalView.current
    val themeMode = appThemeMode()
    SideEffect {
        val window = (view.context as? FragmentActivity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = themeMode == ThemeMode.LIGHT
    }
}

private fun showDatePicker(
    context: android.content.Context,
    initialMillis: Long,
    onDatePicked: (Long) -> Unit,
    onDismiss: () -> Unit = {}
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialMillis }
    val dialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val picked = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            onDatePicked(picked)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    dialog.datePicker.maxDate = System.currentTimeMillis()
    dialog.setOnDismissListener { onDismiss() }
    dialog.show()
}

private fun showTimePicker(
    context: android.content.Context,
    initialMillis: Long,
    onTimePicked: (Int, Int) -> Unit
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialMillis }
    TimePickerDialog(
        context,
        { _, hourOfDay, minute -> onTimePicked(hourOfDay, minute) },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true
    ).show()
}

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = AppText.PERMISSION_TITLE,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = AppText.PERMISSION_DESC,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        Button(onClick = onGrant) {
            Text(text = AppText.PERMISSION_BUTTON)
        }
    }
}

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.finr_logo),
            contentDescription = AppText.LOGO_DESC,
            modifier = Modifier.height(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = AppText.LOADING, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appSplashBg()),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.finr_logo),
                    contentDescription = AppText.LOGO_DESC,
                    modifier = Modifier.height(96.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = AppText.SPLASH_TAGLINE,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Vaporwave2,
                    textAlign = TextAlign.Center
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.pm_logo),
                contentDescription = null,
                modifier = Modifier.height(32.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = AppText.SPLASH_POWERED_BY,
                style = MaterialTheme.typography.bodySmall,
                color = Vaporwave2
            )
        }
    }
}

@Composable
fun SecureGateScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.finr_logo),
            contentDescription = AppText.LOGO_DESC,
            modifier = Modifier.height(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = AppText.SECURE_MODE, style = MaterialTheme.typography.titleMedium)
        Text(
            text = AppText.AUTH_PROMPT,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        Button(onClick = onUnlock) { Text(AppText.UNLOCK) }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
fun SmsListScreen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    onUpdate: (TransactionEntity) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    onAdd: (TransactionEntity) -> Unit,
    onRefresh: ((Int) -> Unit) -> Unit,
    onRecycle: () -> Unit,
    secureEnabled: Boolean,
    onEnableSecure: () -> Unit,
    onDisableSecure: () -> Unit,
    initialSharedText: String?,
    onSharedTextHandled: () -> Unit,
    onSharedTextFailureExit: () -> Unit,
    themeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit
) {
    val initialBatchSize = 120
    val loadMoreBatchSize = 80
    val context = LocalContext.current
    val appVersionText = remember(context) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: ""
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            "${AppText.VERSION} $versionName ($versionCode)"
        }.getOrElse { AppText.VERSION }
    }
    // Local screen state controls navigation overlays and modal flows.
    val uiScope = rememberCoroutineScope()
    val homeListState = rememberLazyListState()
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var adding by remember(initialSharedText) { mutableStateOf(initialSharedText != null) }
    var showCalendar by remember { mutableStateOf(false) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var calendarWholeMonth by remember { mutableStateOf(false) }
    var calendarSelectionMillis by remember { mutableStateOf<Long?>(null) }
    var filterDate by remember { mutableStateOf<LocalDate?>(null) }
    var filterMonth by remember { mutableStateOf<YearMonth?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showEnableConfirm by remember { mutableStateOf(false) }
    var showDisableConfirm by remember { mutableStateOf(false) }
    var showRecycleConfirm by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var pendingThemeMode by remember(themeMode) { mutableStateOf(themeMode) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedExportYear by remember { mutableStateOf<Int?>(null) }
    var selectedExportMonth by remember { mutableStateOf<Int?>(null) } // null => All months
    var exportPicker by remember { mutableStateOf<PickerConfig?>(null) }
    var showDb by remember { mutableStateOf(false) }
    var dbSchema by remember { mutableStateOf<List<DbTableSchema>>(emptyList()) }
    val activity = context as FragmentActivity
    var pendingCsvContent by remember { mutableStateOf<String?>(null) }
    var pendingCsvFileName by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = pendingCsvContent ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray())
            }
            Toast.makeText(context, AppText.EXPORT_SAVE_SUCCESS, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(context, AppText.EXPORT_SAVE_FAIL, Toast.LENGTH_SHORT).show()
        } finally {
            pendingCsvContent = null
            pendingCsvFileName = null
            showExportDialog = false
        }
    }

    fun filteredForExport(year: Int, month: Int?): List<TransactionEntity> {
        return transactions.filter { txn ->
            val d = smsLocalDate(txn.dateMillis)
            d.year == year && (month == null || d.monthValue == month)
        }
    }

    fun buildCsvName(year: Int, month: Int?): String {
        val monthPart = if (month == null) AppText.EXPORT_ALL_MONTHS.lowercase() else "%02d".format(month)
        return "${AppText.CSV_FILE_PREFIX}${year}_$monthPart.csv"
    }

    fun shareCsv(fileName: String, csvContent: String) {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(csvContent)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, AppText.EXPORT_SHARE))
            showExportDialog = false
        } catch (_: Throwable) {
            Toast.makeText(context, AppText.EXPORT_SHARE_FAIL, Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerRefresh(showToast: Boolean = false) {
        if (isRefreshing) return
        isRefreshing = true
        onRefresh { added ->
            isRefreshing = false
            if (showToast) {
                val msg = if (added > 0) AppText.REFRESH_NEW else AppText.REFRESH_NONE
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Screen-level routing: edit/add/db views replace the list body.
    if (editing != null) {
        EditTransactionScreen(
            transaction = editing!!,
            categories = categories,
            onSave = {
                onUpdate(it)
                editing = null
            },
            onDelete = {
                onDelete(it)
                editing = null
            },
            onClose = { editing = null }
        )
        return
    }

    if (adding) {
        AddTransactionScreen(
            categories = categories,
            prefillSharedText = initialSharedText,
            onSharedTextHandled = onSharedTextHandled,
            onSharedTextFailureExit = onSharedTextFailureExit,
            onSave = {
                onAdd(it)
                adding = false
            },
            onClose = { adding = false }
        )
        return
    }

    if (exportPicker != null) {
        FullscreenPicker(
            title = exportPicker!!.title,
            options = exportPicker!!.options,
            onSelect = { selected ->
                exportPicker!!.onSelect(selected)
                exportPicker = null
            },
            onClose = { exportPicker = null }
        )
        return
    }

    // Pull new SMS rows whenever the home feed is shown again.
    LaunchedEffect(Unit) { triggerRefresh() }

    // Also refresh when app becomes visible again.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                triggerRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = {
                Button(onClick = { showAbout = false }) { Text(AppText.OK) }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.finr_logo),
                        contentDescription = AppText.LOGO_DESC,
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = AppText.APP_NAME)
                }
            },
            text = {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = AppText.ABOUT_BUILD)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = appVersionText)
                }
            }
        )
    }

    if (showEnableConfirm) {
        AlertDialog(
            onDismissRequest = { showEnableConfirm = false },
            confirmButton = {
                Button(onClick = {
                    showEnableConfirm = false
                    runBiometricPrompt(activity) { success ->
                        if (success) {
                            onEnableSecure()
                        }
                    }
                }) { Text(AppText.ENABLE) }
            },
            dismissButton = {
                Button(onClick = { showEnableConfirm = false }) { Text(AppText.CANCEL) }
            },
            title = { Text(AppText.ENABLE_SECURE_TITLE) },
            text = { Text(AppText.ENABLE_SECURE_DESC) }
        )
    }

    if (showDisableConfirm) {
        AlertDialog(
            onDismissRequest = { showDisableConfirm = false },
            confirmButton = {
                Button(onClick = {
                    showDisableConfirm = false
                    runBiometricPrompt(activity) { success ->
                        if (success) {
                            onDisableSecure()
                        }
                    }
                }) { Text(AppText.DISABLE) }
            },
            dismissButton = {
                Button(onClick = { showDisableConfirm = false }) { Text(AppText.CANCEL) }
            },
            title = { Text(AppText.DISABLE_SECURE_TITLE) },
            text = { Text(AppText.DISABLE_SECURE_DESC) }
        )
    }

    if (showRecycleConfirm) {
        AlertDialog(
            onDismissRequest = { showRecycleConfirm = false },
            confirmButton = {
                Button(onClick = {
                    showRecycleConfirm = false
                    onRecycle()
                }) { Text(AppText.RECYCLE) }
            },
            dismissButton = {
                Button(onClick = { showRecycleConfirm = false }) { Text(AppText.CANCEL) }
            },
            title = { Text(AppText.RECYCLE_TITLE) },
            text = { Text(AppText.RECYCLE_DESC) }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    onThemeChanged(pendingThemeMode)
                    showThemeDialog = false
                }) { Text(AppText.OK) }
            },
            title = { Text(AppText.THEME_TITLE) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingThemeMode = ThemeMode.LIGHT },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = pendingThemeMode == ThemeMode.LIGHT,
                            onClick = { pendingThemeMode = ThemeMode.LIGHT }
                        )
                        Text(text = AppText.THEME_LIGHT)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingThemeMode = ThemeMode.DARK },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = pendingThemeMode == ThemeMode.DARK,
                            onClick = { pendingThemeMode = ThemeMode.DARK }
                        )
                        Text(text = AppText.THEME_DARK)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingThemeMode = ThemeMode.MIDNIGHT },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = pendingThemeMode == ThemeMode.MIDNIGHT,
                            onClick = { pendingThemeMode = ThemeMode.MIDNIGHT }
                        )
                        Text(text = AppText.THEME_MIDNIGHT)
                    }
                }
            }
        )
    }

    if (showExportDialog) {
        val years = transactions.map { smsLocalDate(it.dateMillis).year }.distinct().sortedDescending()
        val months = selectedExportYear?.let { year ->
            transactions
                .map { smsLocalDate(it.dateMillis) }
                .filter { it.year == year }
                .map { it.monthValue }
                .distinct()
                .sortedDescending()
        }.orEmpty()

        val monthValueLabel = if (selectedExportMonth == null) {
            AppText.EXPORT_ALL_MONTHS
        } else {
            monthLabel(selectedExportMonth!!)
        }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            confirmButton = {
                Row {
                    Button(onClick = {
                        val year = selectedExportYear
                        if (year == null) {
                            Toast.makeText(context, AppText.EXPORT_SELECT_YEAR, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val exportData = filteredForExport(year, selectedExportMonth)
                        if (exportData.isEmpty()) {
                            Toast.makeText(context, AppText.EXPORT_NO_DATA, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val csv = transactionsToCsv(exportData, categories)
                        val fileName = buildCsvName(year, selectedExportMonth)
                        pendingCsvContent = csv
                        pendingCsvFileName = fileName
                        showExportDialog = false
                        saveCsvLauncher.launch(fileName)
                    }) { Text(AppText.EXPORT_SAVE) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val year = selectedExportYear
                        if (year == null) {
                            Toast.makeText(context, AppText.EXPORT_SELECT_YEAR, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val exportData = filteredForExport(year, selectedExportMonth)
                        if (exportData.isEmpty()) {
                            Toast.makeText(context, AppText.EXPORT_NO_DATA, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val csv = transactionsToCsv(exportData, categories)
                        val fileName = buildCsvName(year, selectedExportMonth)
                        shareCsv(fileName, csv)
                    }) { Text(AppText.EXPORT_SHARE) }
                }
            },
            dismissButton = {
                Button(onClick = { showExportDialog = false }) { Text(AppText.CANCEL) }
            },
            title = { Text(AppText.EXPORT_DATA) },
            text = {
                Column {
                    DropdownField(
                        label = AppText.EXPORT_YEAR,
                        value = selectedExportYear?.toString().orEmpty(),
                        onClick = {
                            if (years.isEmpty()) {
                                Toast.makeText(context, AppText.EXPORT_NO_DATA, Toast.LENGTH_SHORT).show()
                                return@DropdownField
                            }
                            exportPicker = PickerConfig(
                                title = AppText.EXPORT_YEAR,
                                options = years.map { it.toString() },
                                onSelect = { yearText ->
                                    selectedExportYear = yearText.toIntOrNull()
                                    selectedExportMonth = null
                                }
                            )
                        }
                    )
                    if (selectedExportYear != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        DropdownField(
                            label = AppText.EXPORT_MONTH,
                            value = monthValueLabel,
                            onClick = {
                                val opts = listOf(AppText.EXPORT_ALL_MONTHS) + months.map { monthLabel(it) }
                                exportPicker = PickerConfig(
                                    title = AppText.EXPORT_MONTH,
                                    options = opts,
                                    onSelect = { picked ->
                                        selectedExportMonth = if (picked.equals(AppText.EXPORT_ALL_MONTHS, ignoreCase = true)) {
                                            null
                                        } else {
                                            months.firstOrNull { monthLabel(it) == picked }
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        )
    }

    if (showCalendar) {
        AlertDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                Button(onClick = {
                    val selectedMillis = calendarSelectionMillis
                    if (selectedMillis == null) {
                        Toast.makeText(context, AppText.SELECT_DATE_FIRST, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val date = smsLocalDate(selectedMillis)
                    if (calendarWholeMonth) {
                        filterMonth = YearMonth.from(date)
                        filterDate = null
                    } else {
                        filterDate = date
                        filterMonth = null
                    }
                    showCalendar = false
                }) { Text(AppText.APPLY) }
            },
            dismissButton = {
                Button(onClick = { showCalendar = false }) { Text(AppText.CANCEL) }
            },
            title = { Text(AppText.CALENDAR_DIALOG_TITLE) },
            text = {
                Column {
                    // Use outer-click field wrapper; direct OutlinedTextField clicks are unreliable here.
                    DropdownField(
                        label = AppText.CALENDAR_FIELD,
                        value = calendarSelectionMillis?.let { formatSmsDate(it) } ?: "",
                        onClick = { showCalendarPicker = true }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = AppText.WHOLE_MONTH)
                        Switch(checked = calendarWholeMonth, onCheckedChange = { calendarWholeMonth = it })
                    }
                }
            }
        )
    }

    LaunchedEffect(showCalendarPicker) {
        if (!showCalendarPicker) return@LaunchedEffect
        val now = System.currentTimeMillis()
        showDatePicker(
            context = context,
            initialMillis = calendarSelectionMillis ?: now,
            onDatePicked = { pickedDate ->
                calendarSelectionMillis = pickedDate
                showCalendarPicker = false
            },
            onDismiss = {
                showCalendarPicker = false
            }
        )
    }

    BackHandler(enabled = filterDate != null || filterMonth != null) {
        // Back from filtered mode returns to full home feed.
        filterDate = null
        filterMonth = null
    }
    BackHandler(enabled = menuExpanded) { menuExpanded = false }

    // Date/month filter is applied before building headers/summaries.
    val filtered = remember(transactions, filterDate, filterMonth) {
        transactions.filter { txn ->
            val date = smsLocalDate(txn.dateMillis)
            when {
                filterDate != null -> date == filterDate
                filterMonth != null -> YearMonth.from(date) == filterMonth
                else -> true
            }
        }
    }

    if (showDb) {
        DbScreen(schema = dbSchema, onBack = { showDb = false })
        return
    }

    if (filtered.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = AppText.NO_TRANSACTIONS)
        }
        return
    }
    var visibleTransactionCount by remember(filtered, filterDate, filterMonth) {
        mutableStateOf(initialBatchSize)
    }
    val currentVisibleCount = visibleTransactionCount.coerceAtMost(filtered.size)
    val visibleTransactions = remember(filtered, currentVisibleCount) {
        filtered.take(currentVisibleCount)
    }
    val items = remember(visibleTransactions) { buildUiItems(visibleTransactions) }
    val shouldLoadMore by remember(homeListState, items, filtered.size, currentVisibleCount) {
        derivedStateOf {
            if (currentVisibleCount >= filtered.size || items.isEmpty()) return@derivedStateOf false
            val lastVisible = homeListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= (items.lastIndex - 8).coerceAtLeast(0)
        }
    }
    LaunchedEffect(shouldLoadMore, filtered.size, currentVisibleCount) {
        if (shouldLoadMore && currentVisibleCount < filtered.size) {
            visibleTransactionCount = (currentVisibleCount + loadMoreBatchSize).coerceAtMost(filtered.size)
        }
    }

    if (filterDate != null || filterMonth != null) {
        // Dedicated filtered view keeps edit/sms-expand behavior available.
        FilteredTransactionsScreen(
            transactions = filtered,
            categories = categories,
            onEdit = { editing = it },
            onBack = {
                filterDate = null
                filterMonth = null
            },
            title = if (filterDate != null) {
                filterDate!!.format(DateTimeFormatter.ofPattern(AppText.DATE_FMT_DAY))
            } else {
                filterMonth!!.format(DateTimeFormatter.ofPattern(AppText.DATE_FMT_MONTH))
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderBar(
                onMenuToggle = { menuExpanded = !menuExpanded },
                onTitleTap = { uiScope.launch { homeListState.animateScrollToItem(0) } },
            )
            val pullRefreshState = rememberPullRefreshState(
                refreshing = isRefreshing,
                onRefresh = { triggerRefresh(showToast = true) }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                LazyColumn(
                    state = homeListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(
                        items = items,
                        key = { item ->
                            when (item) {
                                is UiItem.Transaction -> item.data.id
                                is UiItem.DailySummary -> AppText.KEY_DAILY_PREFIX + item.date
                                is UiItem.MonthlySummary -> AppText.KEY_MONTHLY_PREFIX + item.month
                                is UiItem.DayHeader -> AppText.KEY_DAY_PREFIX + item.date
                            }
                        }
                    ) { item ->
                        // Single list can render transaction, day header, and summary cards.
                        when (item) {
                            is UiItem.Transaction -> TransactionCard(
                                item,
                                categoryById = categoryById,
                                onEdit = { editing = it }
                            )
                            is UiItem.DayHeader -> DayHeaderCard(item.date)
                            is UiItem.DailySummary -> SummaryCard(
                                title = AppText.DAILY_SUMMARY,
                                subtitle = "",
                                debitTotal = item.debitTotal,
                                creditTotal = item.creditTotal
                            )
                            is UiItem.MonthlySummary -> SummaryCard(
                                title = AppText.MONTHLY_SUMMARY,
                                subtitle = item.month.format(DateTimeFormatter.ofPattern(AppText.DATE_FMT_MONTH)),
                                debitTotal = item.debitTotal,
                                creditTotal = item.creditTotal
                            )
                        }
                    }
                }
                FloatingDock(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onAdd = { adding = true },
                    onCalendar = {
                        // Reset prior selection before opening calendar filter dialog.
                        calendarSelectionMillis = null
                        showCalendar = true
                    },
                    onExport = {
                        selectedExportYear = null
                        selectedExportMonth = null
                        showExportDialog = true
                    }
                )
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
        SlideOutMenu(
            visible = menuExpanded,
            secureEnabled = secureEnabled,
            themeMode = themeMode,
            onSecureToggle = { enabled ->
                menuExpanded = false
                if (enabled) showEnableConfirm = true else showDisableConfirm = true
            },
            onThemeClick = {
                pendingThemeMode = themeMode
                menuExpanded = false
                showThemeDialog = true
            },
            onAboutClick = {
                menuExpanded = false
                showAbout = true
            },
            onRecycleClick = {
                menuExpanded = false
                showRecycleConfirm = true
            },
            onDismiss = { menuExpanded = false }
        )
    }
}

@Composable
fun FilteredTransactionsScreen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    onEdit: (TransactionEntity) -> Unit,
    onBack: () -> Unit,
    title: String
) {
    BackHandler { onBack() }
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    Column(modifier = Modifier.fillMaxSize()) {
        val items = remember(transactions) { buildUiItems(transactions) }
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = AppText.NO_TRANSACTIONS)
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 52.dp, start = 16.dp, end = 16.dp)
        ) {
            items(
                items = items,
                key = { item ->
                    when (item) {
                        is UiItem.Transaction -> item.data.id
                        is UiItem.DailySummary -> AppText.KEY_DAILY_PREFIX + item.date
                        is UiItem.MonthlySummary -> AppText.KEY_MONTHLY_PREFIX + item.month
                        is UiItem.DayHeader -> AppText.KEY_DAY_PREFIX + item.date
                    }
                }
            ) { item ->
                when (item) {
                    is UiItem.Transaction -> TransactionCard(
                        item,
                        categoryById = categoryById,
                        onEdit = onEdit
                    )
                    is UiItem.DayHeader -> DayHeaderCard(item.date)
                    is UiItem.DailySummary -> SummaryCard(
                        title = AppText.DAILY_SUMMARY,
                        subtitle = "",
                        debitTotal = item.debitTotal,
                        creditTotal = item.creditTotal
                    )
                    is UiItem.MonthlySummary -> SummaryCard(
                        title = AppText.MONTHLY_SUMMARY,
                        subtitle = item.month.format(DateTimeFormatter.ofPattern(AppText.DATE_FMT_MONTH)),
                        debitTotal = item.debitTotal,
                        creditTotal = item.creditTotal
                    )
                }
            }
        }
    }
}

@Composable
fun DbScreen(
    schema: List<DbTableSchema>,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = AppText.DB_TITLE,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text(AppText.BACK) }
        }
        if (schema.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = AppText.DB_NO_SCHEMA)
            }
            return
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            items(schema, key = { it.name }) { table ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = AppText.DB_TABLE_PREFIX + table.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (table.columns.isEmpty()) {
                            Text(AppText.DB_NO_COLUMNS, style = MaterialTheme.typography.bodySmall)
                        } else {
                            table.columns.forEach { column ->
                                Text(
                                    text = column.name + AppText.COLON_SPACE + column.type.ifBlank { AppText.DB_UNKNOWN },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditTransactionScreen(
    transaction: TransactionEntity,
    categories: List<CategoryEntity>,
    onSave: (TransactionEntity) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    onClose: () -> Unit
) {
    ConfigureLightStatusBarIcons()
    val context = LocalContext.current
    val isUserCreated = transaction.message.equals(AppText.USER_CREATED, ignoreCase = true)
    var txn by remember(transaction.id) { mutableStateOf(transaction.txn) }
    var amountInput by remember(transaction.id) {
        mutableStateOf(parseAmountValue(transaction.amount)?.toPlainString().orEmpty())
    }
    var selectedDateTimeMillis by remember(transaction.id) { mutableStateOf(transaction.dateMillis) }
    var addressInput by remember(transaction.id) { mutableStateOf(transaction.address) }
    val initialClass = categoryForTxnClass(categories, transaction.txnClass)
    var category by remember(transaction.id) { mutableStateOf(initialClass?.name ?: AppText.MISC) }
    var subcategory by remember(transaction.id) { mutableStateOf(initialClass?.subcategory ?: AppText.UNCAT) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerConfig?>(null) }
    var showCategoryEditor by remember(transaction.id) { mutableStateOf(false) }

    // For device SMS entries only classification/txn are editable.
    // User-created entries allow full edits (amount/date/time/address/message).
    val isDirty = txn != transaction.txn ||
        category != (initialClass?.name ?: AppText.MISC) ||
        subcategory != (initialClass?.subcategory ?: AppText.UNCAT) ||
        (isUserCreated && (
            amountInput != (parseAmountValue(transaction.amount)?.toPlainString().orEmpty()) ||
                selectedDateTimeMillis != transaction.dateMillis ||
                addressInput != transaction.address
            ))
    val editableAmount = amountInput.toBigDecimalOrNull()
    val editableDateMillis = selectedDateTimeMillis
    val canSave = if (isUserCreated) {
        isDirty && editableAmount != null && editableAmount >= BigDecimal.ZERO && editableDateMillis <= System.currentTimeMillis()
    } else {
        isDirty
    }

    BackHandler {
        if (isDirty) {
            showDiscardConfirm = true
        } else {
            onClose()
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            confirmButton = {
                Button(onClick = {
                    showDiscardConfirm = false
                    onClose()
                }) { Text(AppText.DISCARD) }
            },
            dismissButton = {
                Button(onClick = { showDiscardConfirm = false })  { Text(AppText.KEEP_EDITING) }
            },
            title = { Text(AppText.DISCARD_CHANGES_TITLE) },
            text = { Text(AppText.UNSAVED_CHANGES) }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    onDelete(transaction)
                }) { Text(AppText.DELETE) }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirm = false }) { Text(AppText.CANCEL) }
            },
            title = { Text(AppText.DELETE_TXN_TITLE) },
            text = { Text(AppText.DELETE_TXN_DESC) }
        )
    }

    val categoryNames = categoryNamesFromDb(categories).ifEmpty { listOf(AppText.MISC) }
    val selectedCategory = if (categoryNames.contains(category)) category else categoryNames.first()
    val subcategoryOptions = subcategoriesFor(categories, selectedCategory)
    val selectedSubcategory = if (subcategoryOptions.contains(subcategory)) subcategory else subcategoryOptions.firstOrNull().orEmpty()
    val txnColor = if (txn.equals(AppText.CREDIT, ignoreCase = true)) TxnCreditAmount else debitAmountColor()

    if (picker != null) {
        // Full-screen picker avoids dropdown clipping/focus issues.
        FullscreenPicker(
            title = picker!!.title,
            options = picker!!.options,
            onSelect = { selected ->
                picker!!.onSelect(selected)
                picker = null
            },
            onClose = { picker = null }
        )
        return
    }

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        if (isUserCreated) {
                            if (editableAmount == null || editableDateMillis > System.currentTimeMillis()) return@Button
                            onSave(
                                transaction.copy(
                                    address = addressInput.trim(),
                                    message = transaction.message,
                                    amount = formatCurrency(editableAmount),
                                    txn = txn,
                                    txnChannel = inferTxnChannel(transaction.message),
                                    bank = bankDetailsFromMessage(transaction.message).first,
                                    bankLogo = transaction.bankLogo,
                                    bankCardNumber = bankDetailsFromMessage(transaction.message).second,
                                    txnClass = txnClassId(selectedCategory, selectedSubcategory),
                                    dateMillis = editableDateMillis,
                                    time = formatSmsTime(editableDateMillis)
                                )
                            )
                        } else {
                            // For parsed SMS rows preserve original amount/date/time/address/message.
                            onSave(
                                transaction.copy(
                                    txn = txn,
                                    txnClass = txnClassId(selectedCategory, selectedSubcategory)
                                )
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        contentColor = if (canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave
                ) {
                    Text(text = AppText.SAVE, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = AppText.EDIT_TXN,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontFamily = RobotoCondensedFamily
                ),
                color = appTextColor()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = txn.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = appTextColor(),
                        modifier = Modifier.clickable {
                            picker = PickerConfig(
                                title = AppText.TXN_TYPE,
                                options = listOf(AppText.DEBIT, AppText.CREDIT),
                                onSelect = { txn = it }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isUserCreated) formatCurrency(amountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                        else formatCurrency(parseAmountValue(transaction.amount) ?: BigDecimal.ZERO),
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                        color = txnColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${formatSmsDate(if (isUserCreated) selectedDateTimeMillis else transaction.dateMillis)}  |  ${formatSmsTime(if (isUserCreated) selectedDateTimeMillis else transaction.dateMillis)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = appTextColor()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isUserCreated) addressInput.ifBlank { AppText.UNKNOWN_SENDER } else transaction.address.ifBlank { AppText.UNKNOWN_SENDER },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, fontFamily = RobotoCondensedFamily),
                color = appTextColor()
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (!isUserCreated) {
                Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, txnCardBorderColor()),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = transaction.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = appTextColor(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }

            if (isUserCreated) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = sanitizeAmountInput(it) },
                    label = { Text(AppText.AMOUNT_RUPEE) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownField(
                    label = AppText.DATE,
                    value = formatSmsDate(selectedDateTimeMillis),
                    onClick = {
                        showDatePicker(
                            context = context,
                            initialMillis = selectedDateTimeMillis,
                            onDatePicked = { pickedDate ->
                                val updated = withDate(selectedDateTimeMillis, pickedDate)
                                if (updated <= System.currentTimeMillis()) {
                                    selectedDateTimeMillis = updated
                                } else {
                                    Toast.makeText(context, AppText.FUTURE_DATE_TIME_NOT_ALLOWED, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                )
                DropdownField(
                    label = AppText.TIME,
                    value = formatSmsTime(selectedDateTimeMillis),
                    onClick = {
                        showTimePicker(
                            context = context,
                            initialMillis = selectedDateTimeMillis
                        ) { hour, minute ->
                            val updated = withTime(selectedDateTimeMillis, hour, minute)
                            if (updated <= System.currentTimeMillis()) {
                                selectedDateTimeMillis = updated
                            } else {
                                Toast.makeText(context, AppText.FUTURE_TIME_NOT_ALLOWED, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    label = { Text(AppText.SENDER) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCategoryEditor = !showCategoryEditor },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppText.CATEGORY.uppercase(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = RobotoCondensedFamily),
                    color = appTextColor()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = AppText.EDIT,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .height(18.dp)
                        .clickable { showCategoryEditor = true }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (!showCategoryEditor) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.clickable { showCategoryEditor = true },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = selectedCategory,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Card(
                        modifier = Modifier.clickable { showCategoryEditor = true },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = selectedSubcategory,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                DropdownField(
                    label = AppText.CATEGORY,
                    value = selectedCategory,
                    onClick = {
                        picker = PickerConfig(
                            title = AppText.CATEGORY,
                            options = categoryNames,
                            onSelect = {
                                category = it
                                subcategory = subcategoriesFor(categories, it).firstOrNull().orEmpty()
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                DropdownField(
                    label = AppText.SUBCATEGORY,
                    value = selectedSubcategory,
                    onClick = {
                        picker = PickerConfig(
                            title = AppText.SUBCATEGORY,
                            options = subcategoryOptions,
                            onSelect = { subcategory = it }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = { showDeleteConfirm = true },
                shape = MaterialTheme.shapes.extraLarge,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF704D),
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = AppText.DELETE_TXN_BUTTON, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun AddTransactionScreen(
    categories: List<CategoryEntity>,
    prefillSharedText: String? = null,
    onSharedTextHandled: () -> Unit = {},
    onSharedTextFailureExit: () -> Unit = {},
    onSave: (TransactionEntity) -> Unit,
    onClose: () -> Unit
) {
    ConfigureLightStatusBarIcons()
    val context = LocalContext.current
    val nowMillis = remember { System.currentTimeMillis() }
    val defaultCategory = categoryNamesFromDb(categories).firstOrNull() ?: AppText.UNCLASSIFIED

    var amountInput by remember { mutableStateOf("") }
    var selectedDateTimeMillis by remember { mutableStateOf(nowMillis) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importSmsInput by remember { mutableStateOf("") }
    var importedSmsBody by remember { mutableStateOf<String?>(null) }
    var txn by remember { mutableStateOf(AppText.DEBIT) }
    var category by remember { mutableStateOf(defaultCategory) }
    var subcategory by remember { mutableStateOf(AppText.UNCAT) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerConfig?>(null) }
    var showSharedImportError by remember { mutableStateOf(false) }

    fun applyImportedText(body: String): Boolean {
        val sms = SmsMessage(address = AppText.USER_T, body = body, dateMillis = selectedDateTimeMillis)
        val extractedAmount = extractAmount(body)
        val extractedType = classifyTransaction(sms)
        if (extractedAmount == null || extractedType == null) return false

        amountInput = parseAmountValue(extractedAmount)?.toPlainString().orEmpty()
        txn = if (extractedType == TransactionType.CREDIT) AppText.CREDIT else AppText.DEBIT
        val inferredClassId = autoTxnClassForMessage(body)
        val inferredClass = inferredClassId?.let { categoryForTxnClass(categories, it) }
        category = inferredClass?.name ?: AppText.MISC
        subcategory = inferredClass?.subcategory ?: AppText.UNCAT
        importedSmsBody = body
        return true
    }

    LaunchedEffect(prefillSharedText) {
        val shared = prefillSharedText?.trim().orEmpty()
        if (shared.isBlank()) return@LaunchedEffect
        val success = applyImportedText(shared)
        if (success) {
            onSharedTextHandled()
            Toast.makeText(context, AppText.IMPORT_SMS_SUCCESS, Toast.LENGTH_SHORT).show()
        } else {
            showSharedImportError = true
        }
    }

    if (showSharedImportError) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    showSharedImportError = false
                    onSharedTextFailureExit()
                }) { Text(AppText.SHARED_TEXT_IMPORT_EXIT) }
            },
            title = { Text(AppText.IMPORT_SMS_FAIL) },
            text = { Text(AppText.SHARED_TEXT_IMPORT_FAIL) }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            confirmButton = {
                Button(onClick = {
                    val body = importSmsInput.trim()
                    if (body.isBlank()) {
                        Toast.makeText(context, AppText.IMPORT_SMS_EMPTY, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!applyImportedText(body)) {
                        importSmsInput = ""
                        Toast.makeText(context, AppText.IMPORT_SMS_FAIL, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    showImportDialog = false
                    Toast.makeText(context, AppText.IMPORT_SMS_SUCCESS, Toast.LENGTH_SHORT).show()
                }) { Text(AppText.SAVE) }
            },
            dismissButton = {
                Button(onClick = { showImportDialog = false }) { Text(AppText.CANCEL) }
            },
            title = { Text(AppText.IMPORT_SMS) },
            text = {
                OutlinedTextField(
                    value = importSmsInput,
                    onValueChange = { importSmsInput = it },
                    label = { Text(AppText.IMPORT_SMS_PASTE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
        )
    }

    val isDirty = amountInput.isNotBlank() ||
        selectedDateTimeMillis != nowMillis ||
        txn != AppText.DEBIT ||
        category != defaultCategory ||
        subcategory.isNotBlank()
    val amountValue = amountInput.toBigDecimalOrNull()
    val selectedDateMillis = selectedDateTimeMillis
    val isValid = amountValue != null && amountValue >= BigDecimal.ZERO && selectedDateMillis <= System.currentTimeMillis()

    BackHandler {
        if (isDirty) {
            showDiscardConfirm = true
        } else {
            onClose()
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            confirmButton = {
                Button(onClick = {
                    showDiscardConfirm = false
                    onClose()
                }) { Text(AppText.DISCARD) }
            },
            dismissButton = {
                Button(onClick = { showDiscardConfirm = false })  { Text(AppText.KEEP_EDITING) }
            },
            title = { Text(AppText.DISCARD_NEW_TITLE) },
            text = { Text(AppText.UNSAVED_CHANGES) }
        )
    }

    val categoryNames = categoryNamesFromDb(categories).ifEmpty { listOf(AppText.MISC) }
    val selectedCategory = if (categoryNames.contains(category)) category else categoryNames.first()
    val subcategoryOptions = subcategoriesFor(categories, selectedCategory)
    val selectedSubcategory = if (subcategoryOptions.contains(subcategory)) subcategory else subcategoryOptions.firstOrNull().orEmpty()

    if (picker != null) {
        // Shared option picker used for txn type/category/subcategory selection.
        FullscreenPicker(
            title = picker!!.title,
            options = picker!!.options,
            onSelect = { selected ->
                picker!!.onSelect(selected)
                picker = null
            },
            onClose = { picker = null }
        )
        return
    }

    Scaffold(
        bottomBar = {
            Button(
                onClick = {
                    if (amountValue == null || selectedDateMillis > System.currentTimeMillis()) return@Button
                    val sourceMessage = importedSmsBody ?: AppText.USER_CREATED
                    val bankInfo = bankDetailsFromMessage(sourceMessage)
                    val bankLogo = inferLogoFromBankName(bankInfo.first)
                    val entity = TransactionEntity(
                        id = UUID.randomUUID().toString(),
                        address = if (importedSmsBody != null) AppText.IMPORTED_SMS_SOURCE else AppText.USER,
                        // Imported SMS is preserved; manual entries use user-created marker.
                        message = sourceMessage,
                        amount = formatCurrency(amountValue),
                        txn = txn,
                        txnChannel = inferTxnChannel(sourceMessage),
                        bank = bankInfo.first,
                        bankLogo = bankLogo,
                        bankCardNumber = bankInfo.second,
                        txnClass = txnClassId(selectedCategory, selectedSubcategory),
                        dateMillis = selectedDateMillis,
                        time = formatSmsTime(selectedDateMillis)
                    )
                    onSave(entity)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = isValid
            ) {
                Text(text = AppText.SAVE, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = AppText.ADD_TXN,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontFamily = RobotoCondensedFamily
                ),
                color = appTextColor()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { showImportDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(AppText.IMPORT_SMS)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = sanitizeAmountInput(it) },
                        label = { Text(AppText.AMOUNT_RUPEE) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownField(
                        label = AppText.DATE,
                        value = formatSmsDate(selectedDateTimeMillis),
                        onClick = {
                            showDatePicker(
                                context = context,
                                initialMillis = selectedDateTimeMillis,
                                onDatePicked = { pickedDate ->
                                    val updated = withDate(selectedDateTimeMillis, pickedDate)
                                    if (updated <= System.currentTimeMillis()) {
                                        selectedDateTimeMillis = updated
                                    } else {
                                        Toast.makeText(context, AppText.FUTURE_DATE_TIME_NOT_ALLOWED, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    )
                    DropdownField(
                        label = AppText.TIME,
                        value = formatSmsTime(selectedDateTimeMillis),
                        onClick = {
                            showTimePicker(
                                context = context,
                                initialMillis = selectedDateTimeMillis
                            ) { hour, minute ->
                                val updated = withTime(selectedDateTimeMillis, hour, minute)
                                if (updated <= System.currentTimeMillis()) {
                                    selectedDateTimeMillis = updated
                                } else {
                                    Toast.makeText(context, AppText.FUTURE_TIME_NOT_ALLOWED, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DropdownField(
                        label = AppText.TXN_TYPE,
                        value = txn,
                        onClick = {
                            picker = PickerConfig(
                                title = AppText.TXN_TYPE,
                                options = listOf(AppText.DEBIT, AppText.CREDIT),
                                onSelect = { txn = it }
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DropdownField(
                        label = AppText.CATEGORY,
                        value = selectedCategory,
                        onClick = {
                            picker = PickerConfig(
                                title = AppText.CATEGORY,
                                options = categoryNames,
                                onSelect = {
                                    category = it
                                    subcategory = subcategoriesFor(categories, it).firstOrNull().orEmpty()
                                }
                            )
                        }
                    )

                    DropdownField(
                        label = AppText.SUBCATEGORY,
                        value = selectedSubcategory,
                        onClick = {
                            picker = PickerConfig(
                                title = AppText.SUBCATEGORY,
                                options = subcategoryOptions,
                                onSelect = { subcategory = it }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun FullscreenPicker(
    title: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text(AppText.CLOSE) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            items(options) { option ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
