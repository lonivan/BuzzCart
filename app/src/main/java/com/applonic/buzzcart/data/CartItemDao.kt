package com.applonic.buzzcart.data

import androidx.room.*
import com.applonic.buzzcart.model.CartItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CartItemDao {

    @Query("SELECT * FROM cart_items")
    fun getAll(): Flow<List<CartItem>>

    @Insert
    suspend fun insert(item: CartItem)

    @Update
    suspend fun update(item: CartItem)

    @Delete
    suspend fun delete(item: CartItem)
}