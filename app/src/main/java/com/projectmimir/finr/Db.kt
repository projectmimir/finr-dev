package com.projectmimir.finr

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class CategoryEntity(
    // Stable compound key: "<category>|<subcategory>".
    @PrimaryKey val id: String,
    val name: String,
    val subcategory: String,
    val type: String,
    val emoji: String
)

@Entity(
    tableName = "transactions",
    indices = [Index("txnClass")],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["txnClass"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val address: String,
    val message: String,
    val amount: String,
    val txn: String,
    val txnChannel: String,
    val bank: String,
    val bankLogo: String,
    val bankCardNumber: String,
    val txnClass: String,
    val dateMillis: Long,
    val time: String
)

@Entity(
    tableName = "senders",
    indices = [Index(value = ["senderId"], unique = true)]
)
data class SenderEntity(
    @PrimaryKey val senderId: String,
    val senderName: String,
    val senderLogo: String
)

@Dao
interface TransactionDao {
    // Main feed query; summaries/grouping are calculated in UI layer.
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

@Dao
interface SenderDao {
    @Query("SELECT * FROM senders")
    suspend fun getAllOnce(): List<SenderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SenderEntity>)
}

@Dao
interface CategoryDao {
    // Master data for category/subcategory pickers.
    @Query("SELECT * FROM categories ORDER BY name ASC, subcategory ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAllOnce(): List<CategoryEntity>

    // IGNORE avoids delete+reinsert behavior that can violate FK constraints from transactions.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAll(items: List<CategoryEntity>)
}

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, SenderEntity::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun senderDao(): SenderDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            // Singleton Room instance across the app process.
            return INSTANCE ?: synchronized(this) {
                val created = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    AppText.DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = created
                created
            }
        }
    }
}
