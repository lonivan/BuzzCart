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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BuzzCartApp()
        }
    }
}

@Composable
fun BuzzCartApp() {
    var text by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(listOf<String>()) }

    Column(modifier = Modifier.padding(16.dp)) {

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
                    items = items + text
                    text = ""
                }
            }) {
                Text("+")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(items) { item ->
                Text(
                    text = "• $item",
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}