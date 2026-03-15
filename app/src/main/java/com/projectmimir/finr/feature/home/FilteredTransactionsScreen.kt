package com.projectmimir.finr

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

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
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        val items = remember(transactions) { buildUiItems(transactions) }
        if (items.isEmpty()) {
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

        LazyColumn(
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
                        onEdit = onEdit
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
    }
}
