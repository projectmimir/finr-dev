package com.projectmimir.finr

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "senders",
    indices = [Index(value = ["senderId"], unique = true)]
)
data class SenderEntity(
    @PrimaryKey val senderId: String,
    val senderName: String,
    val senderLogo: String
)
