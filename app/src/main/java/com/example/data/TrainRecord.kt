package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "train_records")
data class TrainRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val trainNo: String,
    val direction: String,
    val speed: String,
    val positionKm: String,
    val locoModel: String,
    val locoCode: String,
    val route: String,
    val category: String,
    val rssiDb: Float,
    val rawBcd: String
)

@Entity(tableName = "route_station_kms")
data class RouteStationKmEntity(
    @PrimaryKey
    val routeName: String,
    val stationKm: Double,
    val updatedTimestamp: Long
)
