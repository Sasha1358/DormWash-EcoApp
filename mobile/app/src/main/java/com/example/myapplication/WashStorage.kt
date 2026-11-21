package com.example.myapplication // Змініть на свій пакет, якщо він інший

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Допустимі стани локального запису (для підвищеного рівня)
object WashStatus {
    const val PENDING = "очікує"
    const val SENDING = "надсилається"
    const val DONE = "готово"
    const val ERROR = "помилка"
}

data class WashHistoryRecord(
    val id: Long = 0,
    val createdAt: String,
    val fabricType: String,
    val color: String,
    val dirtLevel: String,
    val weightKg: Double,
    val temperature: Int = 0,
    val spinSpeed: Int = 0,
    val durationMinutes: Int = 0,
    val detergentMl: Int = 0,
    val aiExplanation: String = "",
    val status: String = WashStatus.DONE
)

class WashDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "dormwash_history.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "wash_history"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at TEXT NOT NULL,
                fabric_type TEXT NOT NULL,
                color TEXT NOT NULL,
                dirt_level TEXT NOT NULL,
                weight_kg REAL NOT NULL,
                temperature INTEGER NOT NULL,
                spin_speed INTEGER NOT NULL,
                duration_minutes INTEGER NOT NULL,
                detergent_ml INTEGER NOT NULL,
                ai_explanation TEXT NOT NULL,
                status TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertRecord(record: WashHistoryRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("created_at", record.createdAt)
            put("fabric_type", record.fabricType)
            put("color", record.color)
            put("dirt_level", record.dirtLevel)
            put("weight_kg", record.weightKg)
            put("temperature", record.temperature)
            put("spin_speed", record.spinSpeed)
            put("duration_minutes", record.durationMinutes)
            put("detergent_ml", record.detergentMl)
            put("ai_explanation", record.aiExplanation)
            put("status", record.status)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    fun getAllRecords(): List<WashHistoryRecord> {
        val list = mutableListOf<WashHistoryRecord>()
        val db = readableDatabase
        db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY id DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    WashHistoryRecord(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
                        fabricType = cursor.getString(cursor.getColumnIndexOrThrow("fabric_type")),
                        color = cursor.getString(cursor.getColumnIndexOrThrow("color")),
                        dirtLevel = cursor.getString(cursor.getColumnIndexOrThrow("dirt_level")),
                        weightKg = cursor.getDouble(cursor.getColumnIndexOrThrow("weight_kg")),
                        temperature = cursor.getInt(cursor.getColumnIndexOrThrow("temperature")),
                        spinSpeed = cursor.getInt(cursor.getColumnIndexOrThrow("spin_speed")),
                        durationMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("duration_minutes")),
                        detergentMl = cursor.getInt(cursor.getColumnIndexOrThrow("detergent_ml")),
                        aiExplanation = cursor.getString(cursor.getColumnIndexOrThrow("ai_explanation")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                    )
                )
            }
        }
        return list
    }

    fun getRecordById(id: Long): WashHistoryRecord? {
        val db = readableDatabase
        db.rawQuery("SELECT * FROM $TABLE_NAME WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) {
                return WashHistoryRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
                    fabricType = cursor.getString(cursor.getColumnIndexOrThrow("fabric_type")),
                    color = cursor.getString(cursor.getColumnIndexOrThrow("color")),
                    dirtLevel = cursor.getString(cursor.getColumnIndexOrThrow("dirt_level")),
                    weightKg = cursor.getDouble(cursor.getColumnIndexOrThrow("weight_kg")),
                    temperature = cursor.getInt(cursor.getColumnIndexOrThrow("temperature")),
                    spinSpeed = cursor.getInt(cursor.getColumnIndexOrThrow("spin_speed")),
                    durationMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("duration_minutes")),
                    detergentMl = cursor.getInt(cursor.getColumnIndexOrThrow("detergent_ml")),
                    aiExplanation = cursor.getString(cursor.getColumnIndexOrThrow("ai_explanation")),
                    status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                )
            }
        }
        return null
    }

    // Оновлення лише статусу (наприклад, "надсилається" або "помилка")
    fun updateStatus(id: Long, newStatus: String) {
        val values = ContentValues().apply {
            put("status", newStatus)
        }
        writableDatabase.update(TABLE_NAME, values, "id = ?", arrayOf(id.toString()))
    }

    // Додавання отриманого результату ШІ до відкладеного запису та зміна статусу на "готово"
    fun updateRecordResult(
        id: Long,
        completedAt: String,
        temperature: Int,
        spinSpeed: Int,
        durationMinutes: Int,
        detergentMl: Int,
        aiExplanation: String
    ) {
        val values = ContentValues().apply {
            put("created_at", completedAt)
            put("temperature", temperature)
            put("spin_speed", spinSpeed)
            put("duration_minutes", durationMinutes)
            put("detergent_ml", detergentMl)
            put("ai_explanation", aiExplanation)
            put("status", WashStatus.DONE)
        }
        writableDatabase.update(TABLE_NAME, values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteRecord(id: Long) {
        writableDatabase.delete(TABLE_NAME, "id = ?", arrayOf(id.toString()))
    }

    fun deleteAllRecords() {
        writableDatabase.delete(TABLE_NAME, null, null)
    }
}