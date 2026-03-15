package com.projectmimir.finr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun categoryEntitiesFromOptions(): List<CategoryEntity> {
    return categorySeedList.flatMap { seed ->
        seed.subcategory.split("/").map { raw ->
            val sub = raw.trim()
            CategoryEntity(
                id = "${seed.name}|$sub",
                name = seed.name,
                subcategory = sub,
                type = seed.type,
                emoji = seed.emoji
            )
        }
    }
}

suspend fun seedCategories(db: AppDatabase) {
    withContext(Dispatchers.IO) {
        db.categoryDao().upsertAll(categoryEntitiesFromOptions())
    }
}
