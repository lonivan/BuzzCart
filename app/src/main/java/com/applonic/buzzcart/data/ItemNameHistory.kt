package com.applonic.buzzcart.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "item_name_history")
data class ItemNameHistory(
    @PrimaryKey
    val name: String
)