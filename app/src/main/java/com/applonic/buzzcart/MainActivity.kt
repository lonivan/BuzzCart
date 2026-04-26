package com.applonic.buzzcart
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.applonic.buzzcart.data.BuzzCartDatabase
import com.applonic.buzzcart.data.CartItemRepository
import com.applonic.buzzcart.location.GeofenceManager
import com.applonic.buzzcart.notification.NotificationHelper
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.applonic.buzzcart.location.StoreLocation


class MainActivity : ComponentActivity() {
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var geofenceRequest: GeofencingRequest
    private lateinit var geofencePendingIntent: PendingIntent
    //request location permission at runtime
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true

            @SuppressLint("MissingPermission")
            LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    android.util.Log.d(
                        "GEOFENCE",
                        "Current location: ${location?.latitude}, ${location?.longitude}"
                    )
                }
                .addOnFailureListener { error ->
                    android.util.Log.e("GEOFENCE", "Failed to get current location", error)
                }

            if (granted) {
                //register the geofence
                @SuppressLint("MissingPermission")
                geofencingClient.addGeofences(
                    geofenceRequest,
                    geofencePendingIntent
                ).addOnSuccessListener {
                    android.util.Log.d("GEOFENCE", "Geofence registered successfully")
                }.addOnFailureListener { exception ->
                    android.util.Log.e("GEOFENCE", "Geofence registration failed", exception)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            BuzzCartDatabase::class.java,
            "buzzcart_db"
        ).build()

        val dao = db.cartItemDao()
        val repository = CartItemRepository(dao)

        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())

        val geofenceManager = GeofenceManager(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        // Temporary test store location (will be user-selected later)
        val testStore = StoreLocation(
            name = "REWE",
            lat = 53.56538,
            lng = 9.9424233,
            radius = 100f
        )

        val geofence = geofenceManager.createGeofence(
            id = testStore.name,
            lat = testStore.lat,
            lng = testStore.lng,
            radius = testStore.radius
        )

        geofenceRequest = geofenceManager.createRequest(geofence)
        geofencePendingIntent = geofenceManager.createPendingIntent()


        NotificationHelper.createNotificationChannel(this)

        setContent {
            BuzzCartApp(
                repository = repository,
                storeLocation = testStore
            )
        }
    }
}


@Composable
fun BuzzCartApp(repository: CartItemRepository, storeLocation: StoreLocation) {
    val viewModel: BuzzCartViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return BuzzCartViewModel(repository) as T
        }
    })

    var text by remember { mutableStateOf("") }
    val cartItems by viewModel.cartItems.collectAsState(initial = emptyList())
    // UI state for currently selected radius option
    var selectedRadius by remember {
        mutableStateOf(storeLocation.radius)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .systemBarsPadding()
    ) {
        Text(
            text = "BuzzCart",
            style = MaterialTheme.typography.headlineMedium
        )

        // Shows currently selected store settings
        Text(
            text = "Current store: ${storeLocation.name}",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "Radius: ${selectedRadius.toInt()}m",
            style = MaterialTheme.typography.bodySmall
        )

        // Temporary radius options; TODO later these will update and re-register the geofence
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(50f, 100f, 200f, 500f).forEach { radius ->
                Button(
                    onClick = {
                        // Update selected radius when user taps an option
                        selectedRadius = radius
                    }
                ) {
                    Text("${radius.toInt()}m")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add item") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (text.isNotBlank()) {

                        viewModel.addItem(text.trim())
                        text = ""
                    }
                }
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cartItems) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = item.isChecked,
                                onCheckedChange = {
                                    viewModel.toggleItem(item)
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = item.name,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }

                        TextButton(
                            onClick = {
                                viewModel.deleteItem(item)
                            }
                        ){
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
