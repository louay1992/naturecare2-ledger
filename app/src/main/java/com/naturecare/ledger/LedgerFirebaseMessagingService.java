package com.naturecare.ledger;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

/** Data-only FCM messages display notifications even if the WebView is closed. */
public class LedgerFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "naturecare_approvals_v1";

    @Override public void onMessageReceived(RemoteMessage message) {
        Map<String,String> data = message.getData();
        if (!"approvals".equals(data.get("target"))) return;
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID, "اعتمادات NatureCare",
            NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("إشعارات الفواتير والحركات التي تنتظر الموافقة");
        manager.createNotificationChannel(channel);
        String title=data.getOrDefault("title", "NatureCare Ledger");
        String body=data.getOrDefault("body", "يوجد تحديث على دفتر الحسابات");
        Intent open=new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("nc_target","approvals");
        String id=data.getOrDefault("event_id",String.valueOf(System.currentTimeMillis()));
        int code=id.hashCode() & 0x7fffffff;
        PendingIntent pending=PendingIntent.getActivity(this,code,open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build();
        manager.notify(code, notification);
    }
    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        // It is re-registered with Supabase on the next authenticated app opening.
        getSharedPreferences("naturecare_push",MODE_PRIVATE).edit().putString("latest",token).apply();
    }
}
