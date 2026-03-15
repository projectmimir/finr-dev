package com.projectmimir.finr

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.util.UUID

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
    var category by remember(transaction.id) { mutableStateOf(initialClass.name) }
    var subcategory by remember(transaction.id) { mutableStateOf(initialClass.subcategory) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerConfig?>(null) }
    var showCategoryEditor by remember(transaction.id) { mutableStateOf(false) }

    val isDirty = txn != transaction.txn ||
        category != initialClass.name ||
        subcategory != initialClass.subcategory ||
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
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                Button(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.keep_editing)) }
            },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes)) }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    onDelete(transaction)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.delete_txn_title)) },
            text = { Text(stringResource(R.string.delete_txn_desc)) }
        )
    }

    val categoryNames = categoryNamesFromDb(categories).ifEmpty { listOf(AppText.MISC) }
    val selectedCategory = if (categoryNames.contains(category)) category else categoryNames.first()
    val subcategoryOptions = subcategoriesFor(categories, selectedCategory)
    val selectedSubcategory = if (subcategoryOptions.contains(subcategory)) subcategory else subcategoryOptions.firstOrNull().orEmpty()
    val txnColor = if (txn.equals(AppText.CREDIT, ignoreCase = true)) TxnCreditAmount else debitAmountColor()

    if (picker != null) {
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
                            onSave(
                                transaction.copy(
                                    txn = txn,
                                    txnClass = txnClassId(selectedCategory, selectedSubcategory)
                                )
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        contentColor = if (canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave
                ) {
                    Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold)
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
                text = stringResource(R.string.edit_txn),
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
                                title = context.getString(R.string.txn_type),
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
                text = if (isUserCreated) addressInput.ifBlank { context.getString(R.string.unknown_sender) } else transaction.address.ifBlank { context.getString(R.string.unknown_sender) },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, fontFamily = RobotoCondensedFamily),
                color = appTextColor()
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (!isUserCreated) {
                Card(
                    shape = RoundedCornerShape(8.dp),
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
                    label = { Text(stringResource(R.string.amount_rupee)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownField(
                    label = stringResource(R.string.date),
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
                                    Toast.makeText(context, context.getString(R.string.future_date_time_not_allowed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                )
                DropdownField(
                    label = stringResource(R.string.time),
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
                                Toast.makeText(context, context.getString(R.string.future_time_not_allowed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    label = { Text(stringResource(R.string.sender)) },
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
                    text = stringResource(R.string.category).uppercase(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = RobotoCondensedFamily),
                    color = appTextColor()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .height(18.dp)
                        .clickable { showCategoryEditor = true }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (!showCategoryEditor) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryChip(text = selectedCategory, onClick = { showCategoryEditor = true })
                    CategoryChip(text = selectedSubcategory, onClick = { showCategoryEditor = true })
                }
            } else {
                DropdownField(
                    label = stringResource(R.string.category),
                    value = selectedCategory,
                    onClick = {
                        picker = PickerConfig(
                            title = context.getString(R.string.category),
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
                    label = stringResource(R.string.subcategory),
                    value = selectedSubcategory,
                    onClick = {
                        picker = PickerConfig(
                            title = context.getString(R.string.subcategory),
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF704D),
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.delete_txn_button), fontWeight = FontWeight.Bold)
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
            Toast.makeText(context, context.getString(R.string.import_sms_success), Toast.LENGTH_SHORT).show()
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
                }) { Text(stringResource(R.string.shared_text_import_exit)) }
            },
            title = { Text(stringResource(R.string.import_sms_fail)) },
            text = { Text(stringResource(R.string.shared_text_import_fail)) }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            confirmButton = {
                Button(onClick = {
                    val body = importSmsInput.trim()
                    if (body.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.import_sms_empty), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!applyImportedText(body)) {
                        importSmsInput = ""
                        Toast.makeText(context, context.getString(R.string.import_sms_fail), Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    showImportDialog = false
                    Toast.makeText(context, context.getString(R.string.import_sms_success), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                Button(onClick = { showImportDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.import_sms)) },
            text = {
                OutlinedTextField(
                    value = importSmsInput,
                    onValueChange = { importSmsInput = it },
                    label = { Text(stringResource(R.string.import_sms_paste)) },
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
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                Button(onClick = { showDiscardConfirm = false }) { Text(stringResource(R.string.keep_editing)) }
            },
            title = { Text(stringResource(R.string.discard_new_title)) },
            text = { Text(stringResource(R.string.unsaved_changes)) }
        )
    }

    val categoryNames = categoryNamesFromDb(categories).ifEmpty { listOf(AppText.MISC) }
    val selectedCategory = if (categoryNames.contains(category)) category else categoryNames.first()
    val subcategoryOptions = subcategoriesFor(categories, selectedCategory)
    val selectedSubcategory = if (subcategoryOptions.contains(subcategory)) subcategory else subcategoryOptions.firstOrNull().orEmpty()

    if (picker != null) {
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
                Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold)
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
                text = stringResource(R.string.add_txn),
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
                Text(stringResource(R.string.import_sms))
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
                        label = { Text(stringResource(R.string.amount_rupee)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownField(
                        label = stringResource(R.string.date),
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
                                        Toast.makeText(context, context.getString(R.string.future_date_time_not_allowed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    )
                    DropdownField(
                        label = stringResource(R.string.time),
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
                                    Toast.makeText(context, context.getString(R.string.future_time_not_allowed), Toast.LENGTH_SHORT).show()
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
                        label = stringResource(R.string.txn_type),
                        value = txn,
                        onClick = {
                            picker = PickerConfig(
                                title = context.getString(R.string.txn_type),
                                options = listOf(AppText.DEBIT, AppText.CREDIT),
                                onSelect = { txn = it }
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DropdownField(
                        label = stringResource(R.string.category),
                        value = selectedCategory,
                        onClick = {
                            picker = PickerConfig(
                                title = context.getString(R.string.category),
                                options = categoryNames,
                                onSelect = {
                                    category = it
                                    subcategory = subcategoriesFor(categories, it).firstOrNull().orEmpty()
                                }
                            )
                        }
                    )

                    DropdownField(
                        label = stringResource(R.string.subcategory),
                        value = selectedSubcategory,
                        onClick = {
                            picker = PickerConfig(
                                title = context.getString(R.string.subcategory),
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
private fun CategoryChip(
    text: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
