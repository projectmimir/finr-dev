package com.projectmimir.finr

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SenderDao {
    @Query("SELECT * FROM senders")
    suspend fun getAllOnce(): List<SenderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SenderEntity>)
}
