package com.applonic.buzzcart
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.applonic.buzzcart.data.SettingsDataStore
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.applonic.buzzcart.model.StoreLabel
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextDecoration

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

        val settingsDataStore = SettingsDataStore(applicationContext)

        setContent {
            // Observe saved store and radius from DataStore
            val savedSettings by settingsDataStore
                .getSettingsFlow()
                .collectAsState(initial = "REWE" to 200f)

            // Build selected store from saved DataStore values
            val savedStore = testStore.copy(
                name = savedSettings.first,
                radius = savedSettings.second
            )

            BuzzCartApp(
                repository = repository,
                storeLocation = savedStore,
                // Re-create and register geofence with updated radius
                onRadiusChanged = { newRadius ->
                    // Persist selected radius
                    lifecycleScope.launch {
                        settingsDataStore.saveSettings(
                            storeName = savedStore.name,
                            radius = newRadius
                        )
                    }

                    val updatedStore = testStore.copy(radius = newRadius)

                    val geofence = geofenceManager.createGeofence(
                        id = updatedStore.name,
                        lat = updatedStore.lat,
                        lng = updatedStore.lng,
                        radius = updatedStore.radius
                    )

                    geofenceRequest = geofenceManager.createRequest(geofence)

                    @SuppressLint("MissingPermission")
                    geofencingClient.addGeofences(
                        geofenceRequest,
                        geofencePendingIntent
                    )
                },
                // Re-create and register geofence for newly selected store
                onStoreChanged = { selectedStore ->
                    // Persist selected store and its radius
                    lifecycleScope.launch {
                        settingsDataStore.saveSettings(
                            storeName = selectedStore.name,
                            radius = selectedStore.radius
                        )
                    }
                    val geofence = geofenceManager.createGeofence(
                        id = selectedStore.name,
                        lat = selectedStore.lat,
                        lng = selectedStore.lng,
                        radius = selectedStore.radius
                    )

                    geofenceRequest = geofenceManager.createRequest(geofence)

                    @SuppressLint("MissingPermission")
                    geofencingClient.addGeofences(
                        geofenceRequest,
                        geofencePendingIntent
                    )
                }

            )
        }
    }
}


@Composable
fun BuzzCartApp(
    repository: CartItemRepository,
    storeLocation: StoreLocation,
    onRadiusChanged: (Float) -> Unit, // Callback used to notify MainActivity when user selects a new radius
    onStoreChanged: (StoreLocation) -> Unit // Callback to notify MainActivity when user selects a different store
) {
    val viewModel: BuzzCartViewModel =
        viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return BuzzCartViewModel(repository) as T
            }
        })

    var text by remember { mutableStateOf("") }
    val cartItems by viewModel.cartItems.collectAsState(initial = emptyList())
    // UI state for currently selected radius option
    var selectedRadius by remember(storeLocation.radius) {
        mutableStateOf(storeLocation.radius)
    }
    // UI state for currently selected store
    var selectedLabel by remember(storeLocation.name) {
        mutableStateOf(storeLocation)
    }



    // Temporary list of stores for dropdown selection
    val stores = listOf(
        StoreLocation("REWE", 53.5546, 9.9076, selectedRadius),
        StoreLocation("ALDI", 53.5552, 9.9287, selectedRadius),
        StoreLocation("EDEKA", 53.552904, 9.931791, selectedRadius),
        StoreLocation("LIDL", 53.551890, 9.935514, selectedRadius)

    )

    // Controls whether the store dropdown is open
    var storeDropdownExpanded by remember {
        mutableStateOf(false)
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.padding(bottom = 16.dp)
        ) {

            Text(
                text = "BuzzCart",
                style = MaterialTheme.typography.headlineLarge
            )

            Text(
                text = "Never forget what to buy nearby.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Temporary store chips (TODO later these become real labels)
        val storeLabels = listOf(
            StoreLabel("REWE", 53.5546, 9.9076, 100f),
            StoreLabel("ALDI", 53.5552, 9.9287, 100f),
            StoreLabel("EDEKA", 53.552904, 9.931791, 100f),
            StoreLabel("LIDL", 53.551890, 9.935514, 100f),
            StoreLabel("OBI", 37.425, -122.081, 100f)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(storeLabels) { label ->

                FilterChip(
                    selected = selectedLabel.name == label.name,
                    onClick = {
                        selectedLabel = selectedLabel.copy(name = label.name)
                    },
                    label = {
                        Text(label.name)
                    }
                )
            }
        }

        // Dropdown to select store from predefined list
        DropdownMenu(
            expanded = storeDropdownExpanded,
            onDismissRequest = { storeDropdownExpanded = false }
        ) {
            stores.forEach { store ->
                DropdownMenuItem(
                    text = { Text(store.name) },
                    onClick = {
                        // Update selected store and notify MainActivity to re-register geofence
                        selectedLabel = store
                        selectedRadius = store.radius
                        onStoreChanged(store)
                        storeDropdownExpanded = false
                    }
                )
            }
        }

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
                        onRadiusChanged(radius)
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add item") },
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        viewModel.addItem(text)
                        text = ""
                    }
                },
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Show unchecked items first, checked items at the bottom
            val sortedItems = cartItems.sortedBy { it.isChecked }
            items(cartItems) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
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

                            Column(
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (item.isChecked) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                    color = if (item.isChecked) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )

                                Text(
                                    text = "REWE • ALDI",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                viewModel.deleteItem(item)
                            }
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
