package com.applonic.buzzcart.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.applonic.buzzcart.model.CartItem

@Database(entities = [CartItem::class], version = 1)
abstract class BuzzCartDatabase : RoomDatabase() {
    abstract fun cartItemDao(): CartItemDao
}