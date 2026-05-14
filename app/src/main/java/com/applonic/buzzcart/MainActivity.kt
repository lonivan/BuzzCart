package com.applonic.buzzcart
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
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
import com.applonic.buzzcart.data.SettingsDataStore
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.applonic.buzzcart.model.ShoppingLabel
import com.applonic.buzzcart.model.StoreLocation
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextDecoration
import com.applonic.buzzcart.model.CartItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.Job

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
        )
            .fallbackToDestructiveMigration(true)
            .build()

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
            val savedLabels by settingsDataStore.labelsFlow
                .collectAsState(initial = "")

            BuzzCartApp(
                repository = repository,
                storeLocation = savedStore,
                savedLabels = savedLabels,
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
                onLabelsChanged = { labels ->

                    val defaultNames = setOf("MAIN", "REWE", "ALDI", "EDEKA", "LIDL", "OBI")

                    val customLabelsText = labels
                        .filter { it.name !in defaultNames }
                        .joinToString("|") { label ->
                            val store = label.stores.firstOrNull()

                            if (store != null) {
                                "${label.name};${store.lat};${store.lng};${store.radius}"
                            } else {
                                label.name
                            }
                        }

                    lifecycleScope.launch {
                        settingsDataStore.saveLabels(customLabelsText)
                    }
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

fun buildLabelList(savedLabels: String): List<ShoppingLabel> {

    val defaultLabels = listOf(
        ShoppingLabel("MAIN", emptyList()),
        ShoppingLabel("REWE", emptyList()),
        ShoppingLabel("ALDI", emptyList()),
        ShoppingLabel("EDEKA", emptyList()),
        ShoppingLabel("LIDL", emptyList()),
        ShoppingLabel("OBI", emptyList())
    )

    if (savedLabels.isBlank()) {
        return defaultLabels
    }

    val customLabels = savedLabels
        .split("|")
        .filter { it.isNotBlank() }
        .map { encodedLabel ->

            val parts = encodedLabel.split(";")

            val name = parts.getOrNull(0).orEmpty()
            val lat = parts.getOrNull(1)?.toDoubleOrNull()
            val lng = parts.getOrNull(2)?.toDoubleOrNull()
            val radius = parts.getOrNull(3)?.toFloatOrNull()

            val stores =
                if (lat != null && lng != null && radius != null) {
                    listOf(
                        StoreLocation(
                            name = name,
                            lat = lat,
                            lng = lng,
                            radius = radius
                        )
                    )
                } else {
                    emptyList()
                }

            ShoppingLabel(
                name = name,
                stores = stores
            )
        }

    return defaultLabels + customLabels
}
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun BuzzCartApp(
    repository: CartItemRepository,
    storeLocation: StoreLocation,
    savedLabels: String,
    onRadiusChanged: (Float) -> Unit, // Callback used to notify MainActivity when user selects a new radius
    onStoreChanged: (StoreLocation) -> Unit, // Callback to notify MainActivity when user selects a different store
    onLabelsChanged: (List<ShoppingLabel>) -> Unit
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

    var selectedItemForLabeling by remember {
        mutableStateOf<CartItem?>(null)
    }

    var recentlyDeletedItem by remember {
        mutableStateOf<CartItem?>(null)
    }

    var labelAssignmentJob by remember {
        mutableStateOf<Job?>(null)
    }

    var showCreateLabelDialog by remember {
        mutableStateOf(false)
    }

    var newLabelName by remember {
        mutableStateOf("")
    }
    var newLabelLat by remember {
        mutableStateOf("")
    }

    var newLabelLng by remember {
        mutableStateOf("")
    }

    var newLabelRadius by remember {
        mutableStateOf("100")
    }

    var shoppingLabels by remember(savedLabels) {
        mutableStateOf(
            buildLabelList(savedLabels)
        )
    }

    /*
    // Temporary store chips (TODO later these become real labels)
    var shoppingLabels by remember {
        mutableStateOf(
        listOf(

        ShoppingLabel(
            name = "MAIN",
            stores = emptyList()
        ),
        ShoppingLabel(
            name = "REWE",
            stores = listOf(
                StoreLocation("REWE", 53.5546, 9.9076, 100f)
            )
        ),
        ShoppingLabel(
            name = "ALDI",
            stores = listOf(
                StoreLocation("ALDI", 53.5552, 9.9287, 100f)
            )
        ),
        ShoppingLabel(
            name = "EDEKA",
            stores = listOf(
                StoreLocation("EDEKA", 53.552904, 9.931791, 100f)
            )
        ),
        ShoppingLabel(
            name = "LIDL",
            stores = listOf(
                StoreLocation("LIDL", 53.551890, 9.935514, 100f)
            )
        ),
        ShoppingLabel(
            name = "OBI",
            stores = listOf(
                StoreLocation("OBI", 37.425, -122.081, 100f)
            )
        )
    ))} */
    // UI state for currently selected store
    var selectedLabel by remember {
        mutableStateOf(shoppingLabels.first())
    }
    // Shows short feedback messages after user actions
    val snackbarHostState = remember {
        SnackbarHostState()
    }
    val coroutineScope = rememberCoroutineScope()



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

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(bottom = 16.dp)
            ) {

                Text(
                    text = "${selectedLabel.name} list",
                    style = MaterialTheme.typography.headlineLarge
                )

                Text(
                    text = "Items connected to nearby stores and labels.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }



            Spacer(modifier = Modifier.height(12.dp))


            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shoppingLabels) { shoppingLabel ->

                    val labelAssignedToSelectedItem =
                        selectedItemForLabeling?.labels
                            ?.split(",")
                            ?.contains(shoppingLabel.name) == true

                    FilterChip(
                        modifier = Modifier.height(40.dp),
                        selected = labelAssignedToSelectedItem,
                        onClick = {
                            selectedItemForLabeling?.let { selectedItem ->

                                // Use latest item from database state, not stale selected item
                                val currentItem = cartItems.firstOrNull { it.id == selectedItem.id } ?: selectedItem

                                val labels = currentItem.labels
                                    .split(",")
                                    .filter { it.isNotBlank() }

                                val updatedLabels =
                                    if (labels.contains(shoppingLabel.name)) {
                                        labels.filter { it != shoppingLabel.name }
                                    } else {
                                        labels + shoppingLabel.name
                                    }

                                val updatedItem = currentItem.copy(
                                    labels = updatedLabels.joinToString(",")
                                )

                                selectedItemForLabeling = updatedItem
                                viewModel.updateItem(updatedItem)
                                // Keep assignment mode active while user is choosing labels
                                labelAssignmentJob?.cancel()
                                labelAssignmentJob = coroutineScope.launch {
                                    kotlinx.coroutines.delay(8_000)
                                    selectedItemForLabeling = null
                                }
                            } ?: run {
                                selectedLabel = shoppingLabel
                            }
                        },
                        label = {
                            Text(shoppingLabel.name)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (selectedItemForLabeling != null) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },

                            labelColor = if (selectedItemForLabeling != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },

                            selectedContainerColor = if (selectedItemForLabeling != null) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },

                            selectedLabelColor = if (selectedItemForLabeling != null) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            }
                        )
                    )
                }

                item {
                    FilterChip(
                        selected = false,
                        onClick = {
                            showCreateLabelDialog = true
                        },
                        label = {
                            Text("+")
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

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
                    placeholder = {
                        Text("Add item to ${selectedLabel.name}")
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            val itemName = text.trim()
                            viewModel.addItem(
                                name = text,
                                label = selectedLabel.name
                            )
                            coroutineScope.launch  {
                                snackbarHostState.showSnackbar("$itemName added")
                            }
                            text = ""
                        }
                    },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Add")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Show items that belong to the currently selected label/list
            val filteredItems = cartItems.filter { item ->
                item.labels
                    .split(",")
                    .contains(selectedLabel.name)
            }
            // Then sort visible items
            val sortedItems = filteredItems
                .sortedWith(
                    compareBy<CartItem> { it.isChecked }
                        .thenByDescending { it.id }
                )

            if (sortedItems.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "No items yet. Add something you don’t want to forget.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Show unchecked first, newest items first inside each group
                    val sortedItems = filteredItems
                        .sortedWith(
                            compareBy<CartItem> { it.isChecked }
                                .thenByDescending { it.id }
                        )
                    items(sortedItems) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (selectedItemForLabeling?.id == item.id) {

                                            // Tap again to exit assignment mode
                                            selectedItemForLabeling = null
                                        } else {
                                            // Select item for label assignment
                                            selectedItemForLabeling = item
                                            labelAssignmentJob?.cancel()
                                            labelAssignmentJob = coroutineScope.launch {
                                                kotlinx.coroutines.delay(8_000)
                                                selectedItemForLabeling = null
                                            }
                                        }
                                    },
                                ),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedItemForLabeling?.id == item.id) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
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

                                        val visibleLabels = item.labels
                                            .split(",")
                                            .filter { it.isNotBlank() }
                                            .filter { it != selectedLabel.name }

                                        if (visibleLabels.isNotEmpty()) {
                                            Text(
                                                text = visibleLabels.joinToString(" • "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        recentlyDeletedItem = item
                                        viewModel.deleteItem(item)

                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "${item.name} removed",
                                                actionLabel = "UNDO",
                                                duration = SnackbarDuration.Short
                                            )

                                            if (result == SnackbarResult.ActionPerformed) {
                                                recentlyDeletedItem?.let { deletedItem ->
                                                    viewModel.addItem(
                                                        name = deletedItem.name,
                                                        label = deletedItem.labels
                                                    )
                                                }
                                            }

                                            recentlyDeletedItem = null
                                        }
                                    }
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showCreateLabelDialog) {

        AlertDialog(
            onDismissRequest = {
                showCreateLabelDialog = false
                newLabelName = ""
            },

            title = {
                Text("Create label")
            },

            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newLabelName,
                        onValueChange = { newLabelName = it },
                        singleLine = true,
                        placeholder = {
                            Text("Label name")
                        }
                    )

                    OutlinedTextField(
                        value = newLabelLat,
                        onValueChange = { newLabelLat = it },
                        singleLine = true,
                        placeholder = {
                            Text("Latitude (optional)")
                        }
                    )

                    OutlinedTextField(
                        value = newLabelLng,
                        onValueChange = { newLabelLng = it },
                        singleLine = true,
                        placeholder = {
                            Text("Longitude (optional)")
                        }
                    )

                    OutlinedTextField(
                        value = newLabelRadius,
                        onValueChange = { newLabelRadius = it },
                        singleLine = true,
                        placeholder = {
                            Text("Radius in meters")
                        }
                    )
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {

                        val trimmedName = newLabelName.trim()
                        val lat = newLabelLat.toDoubleOrNull()
                        val lng = newLabelLng.toDoubleOrNull()
                        val radius = newLabelRadius.toFloatOrNull() ?: 100f

                        if (trimmedName.isNotBlank()) {
                            val stores =
                                if (lat != null && lng != null) {
                                    listOf(
                                        StoreLocation(
                                            name = trimmedName,
                                            lat = lat,
                                            lng = lng,
                                            radius = radius
                                        )
                                    )
                                } else {
                                    emptyList()
                                }

                            val updatedLabels = shoppingLabels + ShoppingLabel(
                                name = trimmedName,
                                stores = stores
                            )

                            shoppingLabels = updatedLabels
                            onLabelsChanged(updatedLabels)

                            showCreateLabelDialog = false
                            newLabelName = ""
                            newLabelLat = ""
                            newLabelLng = ""
                            newLabelRadius = "100"
                        }
                    }
                ) {
                    Text("Create")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateLabelDialog = false
                        newLabelName = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
