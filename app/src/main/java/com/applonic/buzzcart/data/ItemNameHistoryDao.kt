package com.applonic.buzzcart.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ItemNameHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(itemNameHistory: ItemNameHistory)

    @Query("SELECT name FROM item_name_history ORDER BY name ASC")
    suspend fun getAllNamesOnce(): List<String>

    @Query("DELETE FROM item_name_history WHERE name = :name")
    suspend fun deleteByName(name: String)
}