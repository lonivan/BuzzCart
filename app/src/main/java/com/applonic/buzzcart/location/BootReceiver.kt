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
import com.applonic.buzzcart.location.GeofenceManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {

        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {

            Log.d("BOOT_RECEIVER", "Device rebooted")

            CoroutineScope(Dispatchers.IO).launch {
                val settingsDataStore = SettingsDataStore(context.applicationContext)

                // Read saved store/radius once after reboot
                val savedSettings = settingsDataStore.getSettingsFlow().first()
                val storeName = savedSettings.first
                val radius = savedSettings.second

                Log.d("BOOT_RECEIVER", "Restoring geofence for $storeName with radius $radius")

                val geofenceManager = GeofenceManager(context.applicationContext)
                val geofencingClient = LocationServices.getGeofencingClient(context.applicationContext)
                val geofence = geofenceManager.createGeofence(
                    id = storeName,
                    lat = 37.4219983,
                    lng = -122.084,
                    radius = radius
                )

                val geofenceRequest = geofenceManager.createRequest(geofence)
                val geofencePendingIntent = geofenceManager.createPendingIntent()

                @SuppressLint("MissingPermission")
                geofencingClient.addGeofences(
                    geofenceRequest,
                    geofencePendingIntent
                ).addOnSuccessListener {
                    Log.d("BOOT_RECEIVER", "Geofence restored after reboot")
                }.addOnFailureListener { error ->
                    Log.e("BOOT_RECEIVER", "Failed to restore geofence after reboot", error)
                }
            }
        }
    }
}