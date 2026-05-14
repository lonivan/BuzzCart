package com.applonic.buzzcart.model

data class ShoppingLabel(
    val name: String,
    val stores: List<StoreLocation>
)

data class StoreLocation(
    val name: String,
    val lat: Double,
    val lng: Double,
    val radius: Float
)