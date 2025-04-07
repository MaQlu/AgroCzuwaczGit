package com.example.czujnaaplikacja.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.czujnaaplikacja.R;
import com.example.czujnaaplikacja.receivers.WaterActionReceiver;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class AppFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "agro_alerts";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getData().containsKey("type") &&
                remoteMessage.getData().get("type").equals("low_moisture")) {

            createNotificationChannel();
            showNotification(
                    remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody()
            );
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Alerty wilgotności",
                    NotificationManager.IMPORTANCE_HIGH
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void showNotification(String title, String message) {
        Intent waterIntent = new Intent(this, WaterActionReceiver.class);
        PendingIntent waterPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                waterIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addAction(R.drawable.ic_water, "Podlej", waterPendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(1, builder.build());
    }
}