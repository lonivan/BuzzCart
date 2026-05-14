package com.applonic.buzzcart.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.annotation.SuppressLint
import com.google.android.gms.location.LocationServices
import com.applonic.buzzcart.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.applonic.buzzcart.buildLabelList

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {

            Log.d("BOOT_RECEIVER", "Device rebooted")

            CoroutineScope(Dispatchers.IO).launch {
                val settingsDataStore = SettingsDataStore(context.applicationContext)

                // Read saved store/radius once after reboot
                val savedSettings = settingsDataStore.getSettingsFlow().first()
                val savedLabels = settingsDataStore.labelsFlow.first()
                val labels = buildLabelList(savedLabels)
                val storeName = savedSettings.first
                val radius = savedSettings.second

                Log.d("BOOT_RECEIVER", "Restoring geofence for $storeName with radius $radius")

                val geofenceManager = GeofenceManager(context.applicationContext)
                val geofencingClient = LocationServices.getGeofencingClient(context.applicationContext)
                labels.forEach { label ->
                    label.stores.forEach { store ->

                        val geofence = geofenceManager.createGeofence(
                            id = "${label.name}_${store.name}",
                            lat = store.lat,
                            lng = store.lng,
                            radius = store.radius
                        )

                        val geofenceRequest = geofenceManager.createRequest(geofence)
                        val geofencePendingIntent = geofenceManager.createPendingIntent()

                        @SuppressLint("MissingPermission")
                        geofencingClient.addGeofences(
                            geofenceRequest,
                            geofencePendingIntent
                        ).addOnSuccessListener {
                            Log.d("BOOT_RECEIVER", "Restored geofence for ${label.name}")
                        }.addOnFailureListener { error ->
                            Log.e("BOOT_RECEIVER", "Failed to restore geofence for ${label.name}", error)
                        }
                    }
                }
            }
        }
    }
}