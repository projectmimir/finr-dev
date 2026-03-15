package com.projectmimir.finr

import android.content.Context
import android.database.sqlite.SQLiteDatabase

data class DbColumnSchema(
    val name: String,
    val type: String
)

data class DbTableSchema(
    val name: String,
    val columns: List<DbColumnSchema>
)

fun readDbSchema(context: Context): List<DbTableSchema> {
    val dbPath = context.getDatabasePath(AppText.DB_NAME)
    if (!dbPath.exists()) return emptyList()

    val database = SQLiteDatabase.openDatabase(
        dbPath.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY
    )

    return database.use { db ->
        val tables = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table')",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }

        tables.map { tableName ->
            val columns = mutableListOf<DbColumnSchema>()
            db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                val typeIdx = cursor.getColumnIndex("type")
                while (cursor.moveToNext()) {
                    columns.add(
                        DbColumnSchema(
                            name = if (nameIdx >= 0) cursor.getString(nameIdx) else "",
                            type = if (typeIdx >= 0) cursor.getString(typeIdx) else ""
                        )
                    )
                }
            }
            DbTableSchema(name = tableName, columns = columns)
        }
    }
}
