package com.projectmimir.finr

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
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
    onThemeChanged: (ThemeMode) -> Unit,
    isRefreshing: Boolean
) {
    val initialBatchSize = 120
    val loadMoreBatchSize = 80
    val context = androidx.compose.ui.platform.LocalContext.current
    val appVersionText = remember(context) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: ""
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            context.getString(R.string.version_with_code, versionName, versionCode)
        }.getOrElse { context.getString(R.string.version_label) }
    }
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
    var selectedExportMonth by remember { mutableStateOf<Int?>(null) }
    var exportPicker by remember { mutableStateOf<PickerConfig?>(null) }
    var showDb by remember { mutableStateOf(false) }
    var dbSchema by remember { mutableStateOf<List<DbTableSchema>>(emptyList()) }
    val activity = context as FragmentActivity
    var pendingCsvContent by remember { mutableStateOf<String?>(null) }
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
            Toast.makeText(context, context.getString(R.string.export_save_success), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(context, context.getString(R.string.export_save_fail), Toast.LENGTH_SHORT).show()
        } finally {
            pendingCsvContent = null
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
        val monthPart = if (month == null) context.getString(R.string.export_all_months).lowercase() else "%02d".format(month)
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
            context.startActivity(Intent.createChooser(share, context.getString(R.string.export_share)))
            showExportDialog = false
        } catch (_: Throwable) {
            Toast.makeText(context, context.getString(R.string.export_share_fail), Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerRefresh(showToast: Boolean = false) {
        if (isRefreshing) return
        onRefresh { added ->
            if (showToast) {
                val msg = if (added > 0) context.getString(R.string.refresh_new) else context.getString(R.string.refresh_none)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    LaunchedEffect(Unit) { triggerRefresh() }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
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
                Button(onClick = { showAbout = false }) { Text(stringResource(R.string.ok)) }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.finr_logo),
                        contentDescription = stringResource(R.string.logo_desc),
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.app_name_display))
                }
            },
            text = {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.about_build))
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
                }) { Text(stringResource(R.string.enable)) }
            },
            dismissButton = {
                Button(onClick = { showEnableConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.enable_secure_title)) },
            text = { Text(stringResource(R.string.enable_secure_desc)) }
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
                }) { Text(stringResource(R.string.disable)) }
            },
            dismissButton = {
                Button(onClick = { showDisableConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.disable_secure_title)) },
            text = { Text(stringResource(R.string.disable_secure_desc)) }
        )
    }

    if (showRecycleConfirm) {
        AlertDialog(
            onDismissRequest = { showRecycleConfirm = false },
            confirmButton = {
                Button(onClick = {
                    showRecycleConfirm = false
                    onRecycle()
                }) { Text(stringResource(R.string.recycle)) }
            },
            dismissButton = {
                Button(onClick = { showRecycleConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.recycle_title)) },
            text = { Text(stringResource(R.string.recycle_desc)) }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                Button(onClick = {
                    onThemeChanged(pendingThemeMode)
                    showThemeDialog = false
                }) { Text(stringResource(R.string.ok)) }
            },
            title = { Text(stringResource(R.string.theme_title)) },
            text = {
                Column {
                    ThemeOptionRow(
                        selected = pendingThemeMode == ThemeMode.LIGHT,
                        label = stringResource(R.string.theme_light),
                        onClick = { pendingThemeMode = ThemeMode.LIGHT }
                    )
                    ThemeOptionRow(
                        selected = pendingThemeMode == ThemeMode.DARK,
                        label = stringResource(R.string.theme_dark),
                        onClick = { pendingThemeMode = ThemeMode.DARK }
                    )
                    ThemeOptionRow(
                        selected = pendingThemeMode == ThemeMode.MIDNIGHT,
                        label = stringResource(R.string.theme_midnight),
                        onClick = { pendingThemeMode = ThemeMode.MIDNIGHT }
                    )
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
            stringResource(R.string.export_all_months)
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
                            Toast.makeText(context, context.getString(R.string.export_select_year), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val exportData = filteredForExport(year, selectedExportMonth)
                        if (exportData.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.export_no_data), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val csv = transactionsToCsv(exportData, categories)
                        val fileName = buildCsvName(year, selectedExportMonth)
                        pendingCsvContent = csv
                        showExportDialog = false
                        saveCsvLauncher.launch(fileName)
                    }) { Text(stringResource(R.string.export_save)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val year = selectedExportYear
                        if (year == null) {
                            Toast.makeText(context, context.getString(R.string.export_select_year), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val exportData = filteredForExport(year, selectedExportMonth)
                        if (exportData.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.export_no_data), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val csv = transactionsToCsv(exportData, categories)
                        val fileName = buildCsvName(year, selectedExportMonth)
                        shareCsv(fileName, csv)
                    }) { Text(stringResource(R.string.export_share)) }
                }
            },
            dismissButton = {
                Button(onClick = { showExportDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.export_data)) },
            text = {
                Column {
                    DropdownField(
                        label = stringResource(R.string.export_year),
                        value = selectedExportYear?.toString().orEmpty(),
                        onClick = {
                            if (years.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.export_no_data), Toast.LENGTH_SHORT).show()
                                return@DropdownField
                            }
                            exportPicker = PickerConfig(
                                title = context.getString(R.string.export_year),
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
                            label = stringResource(R.string.export_month),
                            value = monthValueLabel,
                            onClick = {
                                val opts = listOf(context.getString(R.string.export_all_months)) + months.map { monthLabel(it) }
                                exportPicker = PickerConfig(
                                    title = context.getString(R.string.export_month),
                                    options = opts,
                                    onSelect = { picked ->
                                        selectedExportMonth = if (picked.equals(context.getString(R.string.export_all_months), ignoreCase = true)) {
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
                        Toast.makeText(context, context.getString(R.string.select_date_first), Toast.LENGTH_SHORT).show()
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
                }) { Text(stringResource(R.string.apply)) }
            },
            dismissButton = {
                Button(onClick = { showCalendar = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.calendar_dialog_title)) },
            text = {
                Column {
                    DropdownField(
                        label = stringResource(R.string.calendar_field),
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
                        Text(text = stringResource(R.string.whole_month))
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
        filterDate = null
        filterMonth = null
    }
    BackHandler(enabled = menuExpanded) { menuExpanded = false }

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
            Text(text = stringResource(R.string.no_transactions))
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
                        when (item) {
                            is UiItem.Transaction -> TransactionCard(
                                item,
                                categoryById = categoryById,
                                onEdit = { editing = it }
                            )
                            is UiItem.DayHeader -> DayHeaderCard(item.date)
                            is UiItem.DailySummary -> SummaryCard(
                                type = SummaryCardType.DAILY,
                                subtitle = "",
                                debitTotal = item.debitTotal,
                                creditTotal = item.creditTotal
                            )
                            is UiItem.MonthlySummary -> SummaryCard(
                                type = SummaryCardType.MONTHLY,
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
private fun ThemeOptionRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(text = label)
    }
}
