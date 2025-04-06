package com.example.chujglupi.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chujglupi.model.MoistureData
import com.example.chujglupi.model.MoistureRequest
import com.example.chujglupi.model.AutoWateringRequest
import com.example.chujglupi.network.RetrofitClient
import com.example.chujglupi.ui.theme.AgroCzuwaczTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgroCzuwaczTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MoistureControlScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoistureControlScreen() {
    var sensorData by remember { mutableStateOf<MoistureData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var autoWateringEnabled by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Automatyczne odświeżanie przy starcie i co 10 sekund
    LaunchedEffect(Unit) {
        while (true) {
            fetchData(
                onLoading = { isLoading = true },
                onSuccess = { data ->
                    sensorData = data
                    data.autoWatering?.let { autoWateringEnabled = it }
                    errorMessage = null
                },
                onError = { error ->
                    errorMessage = error
                    sensorData = null
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(error)
                    }
                },
                onComplete = { isLoading = false }
            )
            delay(10000)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AgroCzuwacz",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                }
                sensorData != null -> {
                    SensorDataCards(sensorData!!)
                    Spacer(modifier = Modifier.height(24.dp))

                    ControlPanel(
                        currentMoisture = sensorData!!.soilMoisture,
                        desiredMoisture = sensorData!!.desiredMoisture,
                        autoWateringEnabled = autoWateringEnabled,
                        onAutoWateringChange = { newState ->
                            coroutineScope.launch {
                                try {
                                    val response = RetrofitClient.apiService.setAutoWatering(
                                        AutoWateringRequest(newState)
                                    )
                                    if (response.isSuccessful) {
                                        autoWateringEnabled = newState
                                        snackbarHostState.showSnackbar(
                                            "Automatyczne podlewanie ${if (newState) "włączone" else "wyłączone"}"
                                        )
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Błąd: ${e.message}"
                                    Log.e("AutoWatering", "Error", e)
                                }
                            }
                        },
                        onWaterNowClick = {
                            coroutineScope.launch {
                                try {
                                    val response = RetrofitClient.apiService.debugPump()
                                    if (response.isSuccessful) {
                                        snackbarHostState.showSnackbar("Zakończono podlewanie")
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Błąd podlewania: ${e.message}"
                                    Log.e("Watering", "Error", e)
                                }
                            }
                        },
                        onSetMoistureClick = { level ->
                            coroutineScope.launch {
                                try {
                                    Log.d("Moisture", "Setting level to $level")
                                    val response = RetrofitClient.apiService.setDesiredMoisture(
                                        MoistureRequest(level)
                                    )
                                    if (response.isSuccessful) {
                                        snackbarHostState.showSnackbar(
                                            "Ustawiono poziom wilgotności na $level"
                                        )
                                        fetchData() // Wymuś odświeżenie danych
                                    } else {
                                        errorMessage = "Błąd serwera: ${response.code()}"
                                        Log.e("Moisture",
                                            "Error response: ${response.errorBody()?.string()}")
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Błąd: ${e.message}"
                                    Log.e("Moisture", "Error", e)
                                }
                            }
                        }
                    )
                }
                else -> {
                    errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    } ?: run {
                        Text(
                            text = "Brak danych z czujników",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorDataCards(data: MoistureData) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SensorDataCard(
            icon = "🌡️",
            title = "Temperatura",
            value = "%.1f".format(data.temperature),
            unit = "°C",
            color = Color(0xFFD32F2F)
        )

        SensorDataCard(
            icon = "💧",
            title = "Wilgotność powietrza",
            value = "%.1f".format(data.airHumidity),
            unit = "%",
            color = Color(0xFF1976D2)
        )

        SensorDataCard(
            icon = "🌱",
            title = "Wilgotność gleby",
            value = "${data.soilMoisture}",
            unit = "",
            color = Color(0xFF388E3C)
        )

        SensorDataCard(
            icon = "☀️",
            title = "Poziom światła",
            value = "${data.lightLevel}",
            unit = "lx",
            color = Color(0xFFFFA000)
        )

        // Dodana informacja o dacie ostatniej aktualizacji
        Text(
            text = "🕒 Ostatnia aktualizacja: ${data.fullDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SensorDataCard(
    icon: String,
    title: String,
    value: String,
    unit: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$icon $title",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = color.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    currentMoisture: Int,
    desiredMoisture: Int,
    autoWateringEnabled: Boolean,
    onAutoWateringChange: (Boolean) -> Unit,
    onWaterNowClick: () -> Unit,
    onSetMoistureClick: (Int) -> Unit
) {
    var currentLevel by remember(desiredMoisture) {
        mutableStateOf(desiredMoisture)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Aktualna: $currentMoisture",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Text(
                "Zadana: $desiredMoisture",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Slider(
                value = currentLevel.toFloat(),
                onValueChange = { currentLevel = it.toInt() },
                valueRange = 0f..4095f,
                steps = 100,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Wybrana wartość: $currentLevel",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSetMoistureClick(currentLevel) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                )
            ) {
                Text("Ustaw poziom")
            }

            Button(
                onClick = onWaterNowClick,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1976D2)
                )
            ) {
                Text("Podlej teraz")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Switch(
                checked = autoWateringEnabled,
                onCheckedChange = onAutoWateringChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF2E7D32),
                    checkedTrackColor = Color(0xFFA5D6A7),
                    uncheckedThumbColor = Color(0xFFB0BEC5),
                    uncheckedTrackColor = Color(0xFFCFD8DC)
                )
            )
            Text(
                "Automatyczne podlewanie",
                modifier = Modifier.padding(start = 8.dp),
                color = if (autoWateringEnabled) Color(0xFF2E7D32)
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private suspend fun fetchData(
    onLoading: (() -> Unit)? = null,
    onSuccess: ((MoistureData) -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    onComplete: (() -> Unit)? = null
) {
    try {
        onLoading?.invoke()
        Log.d("FetchData", "Starting data fetch...")
        val response = RetrofitClient.apiService.getSensorData()

        if (response.isSuccessful) {
            response.body()?.let { data ->
                Log.d("FetchData", "Data received: $data")
                onSuccess?.invoke(data)
            } ?: run {
                val error = "Brak danych w odpowiedzi"
                Log.e("FetchData", error)
                onError?.invoke(error)
            }
        } else {
            val error = "Błąd serwera: ${response.code()}"
            Log.e("FetchData", "$error, Body: ${response.errorBody()?.string()}")
            onError?.invoke(error)
        }
    } catch (e: HttpException) {
        val error = "Błąd HTTP: ${e.code()}"
        Log.e("FetchData", error, e)
        onError?.invoke(error)
    } catch (e: Exception) {
        val error = "Błąd połączenia: ${e.message}"
        Log.e("FetchData", error, e)
        onError?.invoke(error)
    } finally {
        onComplete?.invoke()
    }
}