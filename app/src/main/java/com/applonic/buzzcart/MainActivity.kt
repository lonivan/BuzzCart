package com.applonic.buzzcart
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.applonic.buzzcart.model.CartItem
import androidx.room.Room
import androidx.compose.runtime.collectAsState
import com.applonic.buzzcart.data.BuzzCartDatabase
import com.applonic.buzzcart.data.CartItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            BuzzCartDatabase::class.java,
            "buzzcart_db"
        ).build()

        val dao = db.cartItemDao()

        setContent {
            BuzzCartApp(dao)
        }
    }
}



@Composable
fun BuzzCartApp(dao: CartItemDao) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val cartItems by dao.getAll().collectAsState(initial = emptyList())

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
                        val newItem = CartItem(name = text.trim())

                        scope.launch(Dispatchers.IO) {
                            dao.insert(newItem)
                        }

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
                                    val updatedItem = item.copy(isChecked = !item.isChecked)

                                    scope.launch(Dispatchers.IO) {
                                        dao.update(updatedItem)
                                    }
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
                                scope.launch(Dispatchers.IO) {
                                    dao.delete(item)
                                }
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
