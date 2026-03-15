package com.projectmimir.finr

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TransactionsRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context)
) {
    val transactions: Flow<List<TransactionEntity>> = db.transactionDao().getAll()
    val categories: Flow<List<CategoryEntity>> = db.categoryDao().getAll()

    suspend fun initialize() {
        seedCategories(db)
        seedSenders(context, db)
    }

    suspend fun syncSms(): Int = syncSmsToDb(context, db)

    suspend fun backfillMissingBankData(): Int = backfillBankColumns(db)

    suspend fun recycleTransactions() {
        recycleTransactions(context, db)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        db.transactionDao().update(transaction)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        db.transactionDao().delete(transaction)
    }

    suspend fun addTransaction(transaction: TransactionEntity) {
        db.transactionDao().upsertAll(listOf(transaction))
    }
}
