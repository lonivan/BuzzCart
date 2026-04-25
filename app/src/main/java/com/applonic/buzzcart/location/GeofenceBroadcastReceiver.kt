package com.applonic.buzzcart.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.runBlocking
import androidx.room.Room
import com.applonic.buzzcart.data.BuzzCartDatabase

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

                db.cartItemDao().getUncheckedItemsOnce()
            }

            val itemText = if (items.isEmpty()) {
                "Your shopping list is empty."
            } else {
                items.mapIndexed { index, item ->
                    "${index + 1}. ${item.name}"
                }.joinToString("\n")
            }

            // notification builder
            val notification = android.app.Notification.Builder(context, "buzzcart_location_reminders")
                .setContentTitle("You are near a store")
                .setContentText("Check your shopping list")
                .setStyle(
                    android.app.Notification.BigTextStyle()
                        .bigText("Buy:\n$itemText")
                )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()

            notificationManager.notify(1, notification)
        }
    }
}