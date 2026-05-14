package com.applonic.buzzcart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.applonic.buzzcart.data.CartItemRepository
import com.applonic.buzzcart.model.CartItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// Handles business logic and communicates between UI and Repository
class BuzzCartViewModel(
    private val repository: CartItemRepository
) : ViewModel() {

    // Flow emits updates automatically when database changes
    val cartItems: Flow<List<CartItem>> = repository.cartItems

    fun addItem(name: String, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(
                CartItem(
                    name = name,
                    labels =
                        if (label == "MAIN") {
                            "MAIN"
                        } else {
                            "MAIN,$label"
                        }
                )
            )
        }
    }

    fun deleteItem(item: CartItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(item)
        }
    }

    fun toggleItem(item: CartItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.update(item.copy(isChecked = !item.isChecked))
        }
    }

    fun addLabelToItem(item: CartItem, label: String) {

        val existingLabels = item.labels
            .split(",")
            .filter { it.isNotBlank() }

        // Avoid duplicate labels
        if (existingLabels.contains(label)) return

        val updatedLabels = (existingLabels + label)
            .joinToString(",")

        val updatedItem = item.copy(labels = updatedLabels)

        viewModelScope.launch {
            repository.update(updatedItem)
        }
    }

    fun updateItem(item: CartItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.update(item)
        }
    }
}