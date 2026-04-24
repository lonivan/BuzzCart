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

    fun addItem(name: String) {
        // Run DB operations on background thread
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(CartItem(name = name))
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
}