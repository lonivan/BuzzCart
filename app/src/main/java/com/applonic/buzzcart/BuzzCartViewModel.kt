package com.applonic.buzzcart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.applonic.buzzcart.data.CartItemDao
import com.applonic.buzzcart.model.CartItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class BuzzCartViewModel(
    private val dao: CartItemDao
) : ViewModel() {

    val cartItems: Flow<List<CartItem>> = dao.getAll()

    fun addItem(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(CartItem(name = name))
        }
    }

    fun deleteItem(item: CartItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(item)
        }
    }

    fun toggleItem(item: CartItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.update(item.copy(isChecked = !item.isChecked))
        }
    }
}