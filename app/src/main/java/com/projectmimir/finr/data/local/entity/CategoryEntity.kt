package com.projectmimir.finr

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val subcategory: String,
    val type: String,
    val emoji: String
)
