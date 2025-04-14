package com.example.czujnaaplikacja.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.czujnaaplikacja.R
import com.example.czujnaaplikacja.model.AutoWateringRequest
import com.example.czujnaaplikacja.model.MoistureData
import com.example.czujnaaplikacja.model.MoistureRequest
import com.example.czujnaaplikacja.network.RetrofitClient
import com.example.czujnaaplikacja.ui.theme.AgroCzuwaczTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoistureControlScreen()
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
    var isDarkTheme by remember { mutableStateOf(false) }
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

    AgroCzuwaczTheme(darkTheme = isDarkTheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Przycisk przełączania trybu ciemnego
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isDarkTheme) "Tryb ciemny" else "Tryb jasny",
                            fontSize = 16.sp
                        )
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { isDarkTheme = it },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // Logo aplikacji
                Image(
                    painter = painterResource(id = R.drawable.logo_agroczuwacz),
                    contentDescription = "Logo AgroCzuwacz",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(bottom = 24.dp)
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
                        SensorDataCards(sensorData!!, isDarkTheme = isDarkTheme)
                        Spacer(modifier = Modifier.height(24.dp))

                        ControlPanel(
                            currentMoisture = sensorData!!.soilMoisture,
                            desiredMoisture = sensorData!!.desiredMoisture,
                            autoWateringEnabled = autoWateringEnabled,
                            pumpDuration = sensorData!!.pumpDuration,
                            onAutoWateringChange = { enabled ->
                                coroutineScope.launch {
                                    try {
                                        val response = RetrofitClient.apiService.setAutoWatering(AutoWateringRequest(enabled))
                                        if (response.isSuccessful) {
                                            snackbarHostState.showSnackbar("Automatyczne podlewanie ${if (enabled) "włączone" else "wyłączone"}")
                                        } else {
                                            snackbarHostState.showSnackbar("Błąd: ${response.code()}")
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Błąd połączenia: ${e.message}")
                                    }
                                }
                            },
                            onWaterNowClick = {
                                coroutineScope.launch {
                                    try {
                                        val response = RetrofitClient.apiService.waterPlant()
                                        if (response.isSuccessful) {
                                            snackbarHostState.showSnackbar("Podlewanie rozpoczęte")
                                        } else {
                                            snackbarHostState.showSnackbar("Błąd: ${response.code()}")
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Błąd połączenia: ${e.message}")
                                    }
                                }
                            },
                            onSetMoistureClick = { level ->
                                coroutineScope.launch {
                                    try {
                                        val response = RetrofitClient.apiService.setDesiredMoisture(MoistureRequest(level))
                                        if (response.isSuccessful) {
                                            snackbarHostState.showSnackbar("Poziom wilgotności ustawiony na $level")
                                        } else {
                                            snackbarHostState.showSnackbar("Błąd: ${response.code()}")
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Błąd połączenia: ${e.message}")
                                    }
                                }
                            },
                            onSetPumpDurationClick = { duration ->
                                coroutineScope.launch {
                                    try {
                                        val response = RetrofitClient.apiService.setPumpDuration(mapOf("duration" to duration))
                                        if (response.isSuccessful) {
                                            snackbarHostState.showSnackbar("Czas działania pompy ustawiony na $duration sekund")
                                        } else {
                                            snackbarHostState.showSnackbar("Błąd: ${response.code()}")
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Błąd połączenia: ${e.message}")
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
}

@Composable
private fun SensorDataCards(data: MoistureData, isDarkTheme: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SensorDataCard(
            icon = "🌡️",
            title = "Temperatura",
            value = "%.1f".format(data.temperature),
            unit = "°C",
            color = Color(0xFFD32F2F),
            isDarkTheme = isDarkTheme
        )

        SensorDataCard(
            icon = "💧",
            title = "Wilgotność powietrza",
            value = "%.1f".format(data.airHumidity),
            unit = "%",
            color = Color(0xFF1976D2),
            isDarkTheme = isDarkTheme
        )

        SensorDataCard(
            icon = "🌱",
            title = "Wilgotność gleby",
            value = "${data.soilMoisture}",
            unit = "",
            color = Color(0xFF388E3C),
            isDarkTheme = isDarkTheme
        )

        SensorDataCard(
            icon = "☀️",
            title = "Poziom światła",
            value = "${data.lightLevel}",
            unit = "lx",
            color = Color(0xFFFFA000),
            isDarkTheme = isDarkTheme
        )

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
    color: Color,
    isDarkTheme: Boolean
) {
    val textColor = if (isDarkTheme) Color.Black else color

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isDarkTheme) Color(0xFFB0BEC5) else Color(0xFFE8F5E9))
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
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = textColor.copy(alpha = 0.8f)
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
    pumpDuration: Int,
    onAutoWateringChange: (Boolean) -> Unit,
    onWaterNowClick: () -> Unit,
    onSetMoistureClick: (Int) -> Unit,
    onSetPumpDurationClick: (Int) -> Unit
) {
    var newPumpDuration by remember { mutableStateOf("") }
    var currentLevel by remember { mutableStateOf(desiredMoisture) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Zadana wilgotność: $desiredMoisture",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
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

        Button(
            onClick = { onSetMoistureClick(currentLevel) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32)
            )
        ) {
            Text("Ustaw poziom wilgotności")
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
                text = "Automatyczne podlewanie",
                modifier = Modifier.padding(start = 8.dp),
                color = if (autoWateringEnabled) Color(0xFF2E7D32)
                else MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = "Aktualny czas działania pompy: $pumpDuration sekund",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = newPumpDuration,
            onValueChange = { newPumpDuration = it },
            label = { Text("Nowy czas działania pompy (s)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Button(
            onClick = {
                val duration = newPumpDuration.toIntOrNull()
                if (duration != null && duration > 0) {
                    onSetPumpDurationClick(duration)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32)
            )
        ) {
            Text("Ustaw Czas")
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