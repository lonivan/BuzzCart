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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BuzzCartApp()
        }
    }
}

data class CartItem(
    val name: String,
    val isChecked: Boolean = false
)

@Composable
fun BuzzCartApp() {
    var text by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<CartItem>()) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .systemBarsPadding()
    ) {

        Row {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add item (milk, eggs...)") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = {
                if (text.isNotBlank()) {
                    items = items + CartItem(name = text)
                    text = ""
                }
            }) {
                Text("+")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(items) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        Checkbox(
                            checked = item.isChecked,
                            onCheckedChange = {
                                items = items.map {
                                    if (it == item) it.copy(isChecked = !it.isChecked)
                                    else it
                                }
                            }
                        )

                        Text(text = item.name)
                    }

                    Button(onClick = {
                        items = items - item
                    }) {
                        Text("X")
                    }
                }
            }
        }
    }
}