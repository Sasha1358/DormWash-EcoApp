package com.example.myapplication // Змініть на свій пакет, якщо він інший

import android.annotation.SuppressLint
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class WashRequest(
    @SerializedName("fabric_type") val fabricType: String,
    val color: String,
    @SerializedName("dirt_level") val dirtLevel: String
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

class MainActivity : AppCompatActivity() {
    @SuppressLint("SetTextI18n", "StringFormatMatches")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etFabric = findViewById<EditText>(R.id.etFabric)
        val btnSend = findViewById<Button>(R.id.btnSend)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.31.60:8000") // Адреса локального сервера для йомулятора
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(DormWashApi::class.java)

        btnSend.setOnClickListener {
            val fabric = etFabric.text.toString()
            if (fabric.isEmpty()) {
                tvResult.text = "Введіть тип тканини!"
                return@setOnClickListener
            }

            tvResult.text = "Завантаження..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val request = WashRequest(fabric, "Світлий", "Середній")
                    val response = api.getEcoMode(request)

                    withContext(Dispatchers.Main) {
                        tvResult.text = getString(
                            R.string.wash_result,
                            response.temperature,      // 1. піде у %1$d
                            response.spinSpeed,        // 2. піде у %2$d
                            response.durationMinutes,  // 3. піде у %3$d
                            response.detergentMl,      // 4. піде у %4$d
                            response.aiExplanation     // 5. піде у %5$s
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvResult.text = "Помилка: ${e.message}"
                    }
                }
            }
        }
    }
}