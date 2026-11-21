package com.example.myapplication // Змініть на свій пакет, якщо він інший

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    companion object {
        // Зберігає ID записів, які зараз у процесі надсилання (захист від дублювання)
        private val inFlightIds = mutableSetOf<Long>()
    }

    private lateinit var dbHelper: WashDatabaseHelper
    private lateinit var tvDetailInfo: TextView
    private lateinit var btnResend: Button
    private lateinit var btnDeleteRecord: Button

    private var recordId: Long = -1L
    private var currentRecord: WashHistoryRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        dbHelper = WashDatabaseHelper(this)
        recordId = intent.getLongExtra("RECORD_ID", -1L)

        tvDetailInfo = findViewById(R.id.tvDetailInfo)
        btnResend = findViewById(R.id.btnResend)
        btnDeleteRecord = findViewById(R.id.btnDeleteRecord)
        val btnBackHistory = findViewById<Button>(R.id.btnBackHistory)

        btnBackHistory.setOnClickListener { finish() }

        btnResend.setOnClickListener {
            val record = currentRecord ?: return@setOnClickListener
            sendPendingRecord(record)
        }

        btnDeleteRecord.setOnClickListener {
            if (inFlightIds.contains(recordId)) {
                Toast.makeText(this, "Зачекайте завершення надсилання", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Видалення запису")
                .setMessage("Ви впевнені, що хочете видалити цей запис з історії?")
                .setPositiveButton("Так, видалити") { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        dbHelper.deleteRecord(recordId)
                        withContext(Dispatchers.Main) {
                            finish()
                        }
                    }
                }
                .setNegativeButton("Скасувати", null)
                .show()
        }

        loadRecord()
    }

    private fun loadRecord() {
        CoroutineScope(Dispatchers.IO).launch {
            val record = if (recordId != -1L) dbHelper.getRecordById(recordId) else null

            withContext(Dispatchers.Main) {
                currentRecord = record
                renderRecord(record)
            }
        }
    }

    private fun renderRecord(record: WashHistoryRecord?) {
        if (record == null) {
            tvDetailInfo.text = "Запис не знайдено."
            btnResend.visibility = View.GONE
            btnDeleteRecord.isEnabled = false
            return
        }

        val isSendingNow = inFlightIds.contains(record.id)
        val effectiveStatus = if (isSendingNow) WashStatus.SENDING else record.status

        val baseInfo = """
            ID запису: #${record.id}
            Дата й час: ${record.createdAt}
            Статус: $effectiveStatus

            --- Вхідні дані ---
            Тип тканини: ${record.fabricType}
            Колір: ${record.color}
            Рівень забруднення: ${record.dirtLevel}
            Вага одягу: ${record.weightKg} кг
        """.trimIndent()

        when (effectiveStatus) {
            WashStatus.DONE -> {
                tvDetailInfo.text = """
                    $baseInfo

                    --- Результат ШІ (історичний) ---
                    Температура: ${record.temperature}°C
                    Віджим: ${record.spinSpeed} об/хв
                    Час прання: ${record.durationMinutes} хв
                    Мийний засіб: ${record.detergentMl} мл

                    Рекомендація:
                    ${record.aiExplanation}
                """.trimIndent()
                btnResend.visibility = View.GONE
                btnDeleteRecord.isEnabled = true
            }

            WashStatus.SENDING -> {
                tvDetailInfo.text = "$baseInfo\n\nВиконується надсилання запиту на сервер..."
                btnResend.visibility = View.VISIBLE
                btnResend.text = "Надсилається..."
                btnResend.isEnabled = false
                btnDeleteRecord.isEnabled = false
            }

            WashStatus.ERROR -> {
                tvDetailInfo.text = "$baseInfo\n\nНе вдалося отримати відповідь від сервера. Перевірте з'єднання та повторіть спробу вручну."
                btnResend.visibility = View.VISIBLE
                btnResend.text = "Повторити надсилання"
                btnResend.isEnabled = true
                btnDeleteRecord.isEnabled = true
            }

            else -> { // WashStatus.PENDING
                tvDetailInfo.text = "$baseInfo\n\nЗапит збережено локально без доступу до сервера. Натисніть «Надіслати» після відновлення зв'язку."
                btnResend.visibility = View.VISIBLE
                btnResend.text = "Надіслати"
                btnResend.isEnabled = true
                btnDeleteRecord.isEnabled = true
            }
        }
    }

    private fun sendPendingRecord(record: WashHistoryRecord) {
        // Захист від повторної одночасної відправки
        if (record.status == WashStatus.DONE || inFlightIds.contains(record.id)) {
            return
        }

        inFlightIds.add(record.id)
        currentRecord = record.copy(status = WashStatus.SENDING)
        renderRecord(currentRecord)

        CoroutineScope(Dispatchers.IO).launch {
            dbHelper.updateStatus(record.id, WashStatus.SENDING)

            try {
                val request = WashRequest(
                    fabricType = record.fabricType,
                    color = record.color,
                    dirtLevel = record.dirtLevel,
                    weight_kg = record.weightKg
                )

                val response = ApiClient.api.getEcoMode(request)
                val completedTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                dbHelper.updateRecordResult(
                    id = record.id,
                    completedAt = completedTime,
                    temperature = response.temperature,
                    spinSpeed = response.spinSpeed,
                    durationMinutes = response.durationMinutes,
                    detergentMl = response.detergentMl,
                    aiExplanation = response.aiExplanation
                )

                val updated = dbHelper.getRecordById(record.id)

                withContext(Dispatchers.Main) {
                    inFlightIds.remove(record.id)
                    if (!isFinishing && !isDestroyed) {
                        currentRecord = updated
                        renderRecord(updated)
                        Toast.makeText(this@DetailActivity, "Результат успішно отримано!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Залишаємо запис у базі зі статусом "помилка" для ручного повтору (без технічних стектрейсів у БД)
                dbHelper.updateStatus(record.id, WashStatus.ERROR)
                val failed = dbHelper.getRecordById(record.id)

                withContext(Dispatchers.Main) {
                    inFlightIds.remove(record.id)
                    if (!isFinishing && !isDestroyed) {
                        currentRecord = failed
                        renderRecord(failed)
                        Toast.makeText(
                            this@DetailActivity,
                            "Не вдалося надіслати. Перевірте сервер і спробуйте ще раз.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}