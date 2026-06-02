package com.charles.trailsage.firebase

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TrailSageMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val kind = message.data["kind"] ?: return
        if (kind !in setOf("tour_pack_update", "featured_destination", "voice_pack_update", "model_pack_update")) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("updates", "TrailSage updates", NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(this, "updates").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(message.notification?.title ?: "TrailSage update")
            .setContentText(message.notification?.body ?: "An offline pack update is available.").setAutoCancel(true).build()
        NotificationManagerCompat.from(this).notify(kind.hashCode(), notification)
    }
}
