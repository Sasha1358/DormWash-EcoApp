package com.example.myapplication // Змініть на свій пакет, якщо він інший

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WashRequest(
    @SerializedName("fabric_type") val fabricType: String,
    val color: String,
    @SerializedName("dirt_level") val dirtLevel: String,
    val weight_kg: Double
)

data class WashResponse(
    val temperature: Int,
    @SerializedName("spin_speed") val spinSpeed: Int,
    @SerializedName("duration_minutes") val durationMinutes: Int,
    @SerializedName("detergent_ml") val detergentMl: Int,
    @SerializedName("ai_explanation") val aiExplanation: String
)

interface DormWashApi {
    @POST("/api/eco-mode")
    suspend fun getEcoMode(@Body request: WashRequest): WashResponse
}

// Спільний клієнт Retrofit для MainActivity та DetailActivity
object ApiClient {
    private const val BASE_URL = "http://192.168.31.60:8000" // Перевірте актуальний локальний IP

    val api: DormWashApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DormWashApi::class.java)
    }
}

class MainActivity : AppCompatActivity() {

    private var isRequestInProgress = false

    @SuppressLint("SetTextI18n", "StringFormatMatches")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etFabric = findViewById<EditText>(R.id.etFabric)
        val etWeight = findViewById<EditText>(R.id.etWeight)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val btnHistory = findViewById<Button>(R.id.btnHistory)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        val dbHelper = WashDatabaseHelper(this)

        btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnSend.setOnClickListener {
            if (isRequestInProgress) return@setOnClickListener

            val fabric = etFabric.text.toString().trim()
            if (fabric.isEmpty()) {
                tvResult.text = "Введіть тип тканини!"
                return@setOnClickListener
            }

            val weightStr = etWeight.text.toString().trim().replace(',', '.')
            val weight = weightStr.toDoubleOrNull()

            if (weight == null || weight <= 0 || weight > 20.0) {
                etWeight.error = "Введіть вагу від 0.1 до 20 кг"
                return@setOnClickListener
            }

            isRequestInProgress = true
            btnSend.isEnabled = false
            tvResult.text = "Надсилається запит..."

            CoroutineScope(Dispatchers.IO).launch {
                val color = "Світлий"
                val dirtLevel = "Середній"
                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

                try {
                    val request = WashRequest(
                        fabricType = fabric,
                        color = color,
                        dirtLevel = dirtLevel,
                        weight_kg = weight
                    )

                    val response = ApiClient.api.getEcoMode(request)

                    val historyRecord = WashHistoryRecord(
                        createdAt = currentTime,
                        fabricType = fabric,
                        color = color,
                        dirtLevel = dirtLevel,
                        weightKg = weight,
                        temperature = response.temperature,
                        spinSpeed = response.spinSpeed,
                        durationMinutes = response.durationMinutes,
                        detergentMl = response.detergentMl,
                        aiExplanation = response.aiExplanation,
                        status = WashStatus.DONE
                    )
                    dbHelper.insertRecord(historyRecord)

                    withContext(Dispatchers.Main) {
                        tvResult.text = getString(
                            R.string.wash_result,
                            response.temperature,
                            response.spinSpeed,
                            response.durationMinutes,
                            response.detergentMl,
                            response.aiExplanation
                        )
                    }
                } catch (e: IOException) {
                    // Немає доступу до backend / мережі: зберігаємо запит локально зі статусом "очікує"
                    val pendingRecord = WashHistoryRecord(
                        createdAt = currentTime,
                        fabricType = fabric,
                        color = color,
                        dirtLevel = dirtLevel,
                        weightKg = weight,
                        status = WashStatus.PENDING
                    )
                    dbHelper.insertRecord(pendingRecord)

                    withContext(Dispatchers.Main) {
                        tvResult.text = "Немає зв'язку із сервером.\nЗапит збережено в історію зі статусом «очікує» — надішліть його вручну після відновлення мережі."
                    }
                } catch (e: HttpException) {
                    // Помилка backend (400/422/500/502) не зберігається як готовий результат
                    withContext(Dispatchers.Main) {
                        tvResult.text = "Помилка сервера (HTTP ${e.code()}). Запис не збережено."
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvResult.text = "Помилка: ${e.message}"
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isRequestInProgress = false
                        btnSend.isEnabled = true
                    }
                }
            }
        }
    }
}