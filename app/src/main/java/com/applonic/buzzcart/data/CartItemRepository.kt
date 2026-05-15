package com.applonic.buzzcart.data

import com.applonic.buzzcart.model.CartItem
import kotlinx.coroutines.flow.Flow

// Single source of data operations (Room for now, can add API later)
class CartItemRepository(
    private val dao: CartItemDao,
    private val itemNameHistoryDao: ItemNameHistoryDao
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

    suspend fun getAllItemNamesOnce(): List<String> {
        return dao.getAllItemNamesOnce()
    }

    suspend fun saveRemovedItemName(name: String) {
        itemNameHistoryDao.insert(
            ItemNameHistory(name)
        )
    }

    suspend fun getRemovedItemNamesOnce(): List<String> {
        return itemNameHistoryDao.getAllNamesOnce()
    }

    suspend fun removeItemNameFromHistory(name: String) {
        itemNameHistoryDao.deleteByName(name)
    }
}