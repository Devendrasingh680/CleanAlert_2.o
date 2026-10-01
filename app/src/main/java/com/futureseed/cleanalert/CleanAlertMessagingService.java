package com.futureseed.cleanalert;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Receives pushes sent by the Cloud Functions in /functions (see onSubmissionStatusChange).
 * This class only displays whatever the backend actually sent \u2014 it never invents a
 * notification on its own.
 */
public class CleanAlertMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "cleanalert_default";
    private static int nextNotificationId = 1000;

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "CleanAlert updates", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Waste pickup, points, and reward updates");
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // MainActivity registers the token itself right after login (it knows who's signed
        // in); a token refresh while the app isn't in the foreground is rare enough that we
        // don't chase it here to keep this service simple.
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        RemoteMessage.Notification notification = message.getNotification();
        String title = notification != null ? notification.getTitle() : message.getData().get("title");
        String body = notification != null ? notification.getBody() : message.getData().get("body");
        if (title == null && body == null) return;

        createChannel(this);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        try {
            NotificationManagerCompat.from(this).notify(nextNotificationId++, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS was denied \u2014 the push still arrived, we just can't show it.
        }
    }
}
