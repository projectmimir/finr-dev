package com.projectmimir.finr

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE bank = '' OR bankLogo = '' OR bankCardNumber = ''")
    suspend fun getWithoutBankInfo(): List<TransactionEntity>

    @Query("SELECT MAX(dateMillis) FROM transactions")
    suspend fun getLatestDateMillis(): Long?

    @Query("SELECT * FROM transactions WHERE dateMillis >= :fromInclusive AND dateMillis < :toExclusive")
    suspend fun getInRange(fromInclusive: Long, toExclusive: Long): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TransactionEntity>)

    @Update
    suspend fun update(item: TransactionEntity)

    @Delete
    suspend fun delete(item: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
