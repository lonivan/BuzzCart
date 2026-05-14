package com.applonic.buzzcart.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cart_items")
data class CartItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val isChecked: Boolean = false,
    val labels: String = ""
)