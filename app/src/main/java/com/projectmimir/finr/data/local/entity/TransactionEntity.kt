package com.projectmimir.finr

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
