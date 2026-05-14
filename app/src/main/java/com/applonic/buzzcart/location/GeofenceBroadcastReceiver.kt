package com.applonic.buzzcart.location

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

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) return

        if (geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val notificationManager =
                context.getSystemService(android.app.NotificationManager::class.java)

            val items = runBlocking {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    BuzzCartDatabase::class.java,
                    "buzzcart_db"
                ).build()

                val triggeringGeofences = geofencingEvent.triggeringGeofences
                val labelNames = triggeringGeofences
                    ?.mapNotNull { geofence ->
                        geofence.requestId.substringBefore("_")
                    }
                    ?: emptyList()

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

            val itemText = if (items.isEmpty()) {
                "Your shopping list is empty."
            } else {
                items.mapIndexed { index, item ->
                    "${index + 1}. ${item.name}"
                }.joinToString("\n")
            }

           // Opens app when user taps notification
            val intent = Intent(context, com.applonic.buzzcart.MainActivity::class.java)

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // notification builder
            val notification = android.app.Notification.Builder(context, "buzzcart_location_reminders")
                .setContentTitle("You are near a store")
                .setContentText("Check your shopping list")
                .setStyle(
                    android.app.Notification.BigTextStyle()
                        .bigText("Buy:\n$itemText")
                )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent) // opens app when user taps notification
                .setAutoCancel(true) // removes notification after tap
                .build()

            notificationManager.notify(1, notification)
        }
    }
}