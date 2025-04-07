// app/src/main/java/com/example/chujglupi/receivers/WaterActionReceiver.kt
package com.example.czujnaaplikacja.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.czujnaaplikacja.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WaterActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.apiService.waterPlant()
                // Możesz dodać notyfikację o sukcesie
            } catch (e: Exception) {
                // Obsługa błędu
            }
        }
    }
}