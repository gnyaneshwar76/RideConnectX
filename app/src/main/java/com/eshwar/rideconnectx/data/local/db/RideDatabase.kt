package com.eshwar.rideconnectx.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RideEntity::class,
        NotificationEntity::class,
        ServiceRecordEntity::class,
        EmergencyContactEntity::class,
        // ponytail: fuel_samples and refuels are unused since mileage was dropped
        // (11 Sep 2026). Kept to avoid a schema bump before ship; drop both in
        // the next migration.
        FuelSampleEntity::class,
        ServiceTaskEntity::class,
        RefuelEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class RideDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao
    abstract fun notificationDao(): NotificationDao
    abstract fun serviceRecordDao(): ServiceRecordDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun fuelSampleDao(): FuelSampleDao
    abstract fun serviceTaskDao(): ServiceTaskDao
    abstract fun refuelDao(): RefuelDao
}
