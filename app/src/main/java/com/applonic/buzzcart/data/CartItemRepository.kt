package com.applonic.buzzcart.data

import com.applonic.buzzcart.model.CartItem
import kotlinx.coroutines.flow.Flow

// Single source of data operations (Room for now, can add API later)
class CartItemRepository(
    private val dao: CartItemDao
) {
    val cartItems: Flow<List<CartItem>> = dao.getAll()

    suspend fun insert(item: CartItem) {
        dao.insert(item)
    }

    suspend fun delete(item: CartItem) {
        dao.delete(item)
    }

    suspend fun update(item: CartItem) {
        dao.update(item)
    }
}