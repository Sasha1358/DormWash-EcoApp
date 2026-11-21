package com.example.myapplication // Змініть на свій пакет, якщо він інший

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var dbHelper: WashDatabaseHelper
    private lateinit var lvHistory: ListView
    private lateinit var tvHistoryStatus: TextView
    private lateinit var btnClearAll: Button
    private var records: List<WashHistoryRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        dbHelper = WashDatabaseHelper(this)
        lvHistory = findViewById(R.id.lvHistory)
        tvHistoryStatus = findViewById(R.id.tvHistoryStatus)
        btnClearAll = findViewById(R.id.btnClearAll)
        val btnBackMain = findViewById<Button>(R.id.btnBackMain)

        btnBackMain.setOnClickListener { finish() }

        lvHistory.setOnItemClickListener { _, _, position, _ ->
            val selectedRecord = records[position]
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("RECORD_ID", selectedRecord.id)
            startActivity(intent)
        }

        btnClearAll.setOnClickListener {
            if (records.isEmpty()) return@setOnClickListener

            AlertDialog.Builder(this)
                .setTitle("Очищення історії")
                .setMessage("Ви впевнені, що хочете видалити всі збережені записи?")
                .setPositiveButton("Видалити все") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        dbHelper.deleteAllRecords()
                        withContext(Dispatchers.Main) {
                            loadHistory()
                        }
                    }
                }
                .setNegativeButton("Скасувати", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        tvHistoryStatus.text = "Завантаження історії..."
        tvHistoryStatus.visibility = View.VISIBLE
        lvHistory.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val loadedRecords = dbHelper.getAllRecords()

            withContext(Dispatchers.Main) {
                records = loadedRecords
                if (records.isEmpty()) {
                    tvHistoryStatus.text = "Історія прань поки порожня.\nЗробіть перший запит на головному екрані."
                    tvHistoryStatus.visibility = View.VISIBLE
                    lvHistory.visibility = View.GONE
                    btnClearAll.isEnabled = false
                } else {
                    tvHistoryStatus.visibility = View.GONE
                    lvHistory.visibility = View.VISIBLE
                    btnClearAll.isEnabled = true

                    val displayItems = records.map { r ->
                        val summary = when (r.status) {
                            WashStatus.DONE -> "${r.temperature}°C, ${r.durationMinutes} хв, ${r.detergentMl} мл"
                            WashStatus.PENDING -> "Очікує ручної відправки"
                            WashStatus.SENDING -> "Виконується надсилання..."
                            WashStatus.ERROR -> "Помилка — натисніть для повтору"
                            else -> r.status
                        }
                        "${r.createdAt} • [${r.status}]\n${r.fabricType} (${r.weightKg} кг) → $summary"
                    }

                    val adapter = ArrayAdapter(
                        this@HistoryActivity,
                        android.R.layout.simple_list_item_1,
                        displayItems
                    )
                    lvHistory.adapter = adapter
                }
            }
        }
    }
}