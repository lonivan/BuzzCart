package com.applonic.buzzcart.location

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.runBlocking
import androidx.room.Room
import com.applonic.buzzcart.data.BuzzCartDatabase
import android.app.PendingIntent




class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @SuppressLint("UseKtx")
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("GEOFENCE", "GeofenceBroadcastReceiver received event")

        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) return

        if (geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val notificationManager =
                context.getSystemService(android.app.NotificationManager::class.java)

            val triggeringGeofences = geofencingEvent.triggeringGeofences

            val labelNames = triggeringGeofences
                ?.mapNotNull { geofence ->
                    geofence.requestId.substringBefore("_")
                }
                ?: emptyList()

            // Opens app when user taps notification
            val openedLabelName = labelNames.firstOrNull() ?: "MAIN"
            android.util.Log.d("GEOFENCE", "Opening label from notification: $openedLabelName")

            // Prevent repeated notifications for the same label within 2 minutes
            val preferences = context.getSharedPreferences("notification_state", Context.MODE_PRIVATE)
            val lastNotificationTime = preferences.getLong(openedLabelName, 0L)

//            val currentTime = System.currentTimeMillis()
//            if (currentTime - lastNotificationTime < 1 * 60 * 1000) {
//                return
//            }


            val items = runBlocking {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    BuzzCartDatabase::class.java,
                    "buzzcart_db"
                ).build()

                db.cartItemDao()
                    .getUncheckedItemsOnce()
                    .filter { item ->
                        labelNames.any { labelName ->
                            item.labels
                                .split(",")
                                .contains(labelName)
                        }
                    }
            }

            if (items.isEmpty()) {
                return
            }

            // Limit notification size to keep it readable
            val visibleNotificationItems = items.take(5)
            val hiddenItemCount = items.size - visibleNotificationItems.size
            val itemText = buildString {
                visibleNotificationItems.forEach { item ->
                    appendLine("• ${item.name}")
                }

                if (hiddenItemCount > 0) {
                    append("+ $hiddenItemCount more")
                }
            }

            val intent = Intent(context, com.applonic.buzzcart.MainActivity::class.java).apply {
                putExtra("opened_label_name", openedLabelName)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // notification builder
            val notification = android.app.Notification.Builder(context, "buzzcart_location_reminders")
                .setContentTitle("You're near $openedLabelName")
                .setContentText("${items.size} item(s) waiting")
                .setStyle(
                    android.app.Notification.BigTextStyle()
                        .bigText("Buy:\n$itemText")
                )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent) // opens app when user taps notification
                .setAutoCancel(true) // removes notification after tap
                .build()

            // Remember when this label last showed a notification
            /*preferences.edit()
                .putLong(openedLabelName, currentTime)
                .apply()*/

            notificationManager.notify(1, notification)
        }
    }


}