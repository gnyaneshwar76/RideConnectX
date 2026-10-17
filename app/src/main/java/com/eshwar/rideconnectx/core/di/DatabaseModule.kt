package com.eshwar.rideconnectx.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.eshwar.rideconnectx.data.local.db.EmergencyContactDao
import com.eshwar.rideconnectx.data.local.db.FuelSampleDao
import com.eshwar.rideconnectx.data.local.db.NotificationDao
import com.eshwar.rideconnectx.data.local.db.RefuelDao
import com.eshwar.rideconnectx.data.local.db.RideDao
import com.eshwar.rideconnectx.data.local.db.RideDatabase
import com.eshwar.rideconnectx.data.local.db.ServiceRecordDao
import com.eshwar.rideconnectx.data.local.db.ServiceTaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * v1 → v2: adds the `notifications` table. Rides are untouched, so the
     * rider's history survives the upgrade.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `notifications` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `createdAt` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `unread` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v2 → v3: adds the `service_records` table. Existing rides and
     * notifications are untouched.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `service_records` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `servicedAt` INTEGER NOT NULL,
                    `centre` TEXT NOT NULL,
                    `odometerKm` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v3 → v4: adds `emergency_contacts`, with the unique index on the
     * normalised number that keeps duplicate contacts out.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `emergency_contacts` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `name` TEXT NOT NULL,
                    `phone` TEXT NOT NULL,
                    `normalizedPhone` TEXT NOT NULL,
                    `isPrimary` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_emergency_contacts_normalizedPhone` " +
                    "ON `emergency_contacts` (`normalizedPhone`)"
            )
        }
    }

    /** v4 → v5: adds `fuel_samples`, which the mileage estimate is derived from. */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `fuel_samples` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `odometerKm` INTEGER NOT NULL,
                    `segments` INTEGER NOT NULL,
                    `recordedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /** v5 → v6: the fixed four upcoming tasks become an editable table. */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `service_tasks` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `label` TEXT NOT NULL,
                    `everyKm` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v6 → v7: adds the `refuels` table — the rider's fuel log, which is what
     * turns mileage from an inference off the five-segment bar into arithmetic.
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `refuels` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `filledAt` INTEGER NOT NULL,
                    `odometerKm` INTEGER NOT NULL,
                    `tripBKm` REAL NOT NULL,
                    `litres` REAL NOT NULL,
                    `costRupees` REAL NOT NULL,
                    `fullTank` INTEGER NOT NULL,
                    `notes` TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }


    /**
     * v8 — every table gains `ownerId`.
     *
     * One database file is shared by every account that signs in on the phone,
     * and nothing said which rows belonged to whom, so the second person to
     * sign in saw the first person's emergency contacts, rides, notifications
     * and service history.
     *
     * Existing rows become `guest`: they were created before anyone could be
     * identified, and the account that next signs in claims them. Guessing an
     * account for them would be inventing ownership.
     *
     * The contacts uniqueness index is rebuilt as a composite. Keeping it on
     * `normalizedPhone` alone would stop a second account from ever saving a
     * number the first account already has.
     */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val tables = listOf(
                "rides", "notifications", "service_records", "service_tasks",
                "refuels", "fuel_samples", "emergency_contacts",
            )
            tables.forEach { table ->
                db.execSQL("ALTER TABLE $table ADD COLUMN ownerId TEXT NOT NULL DEFAULT 'guest'")
            }
            // Every read filters on ownerId, so it needs an index on each table.
            tables.filter { it != "emergency_contacts" }.forEach { table ->
                db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_ownerId ON $table(ownerId)")
            }
            db.execSQL("DROP INDEX IF EXISTS index_emergency_contacts_normalizedPhone")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_emergency_contacts_ownerId_normalizedPhone " +
                    "ON emergency_contacts(ownerId, normalizedPhone)"
            )
        }
    }

    /**
     * v9 - GPS rides. Drops and rebuilds ONLY the `rides` table (the old rows
     * came from the trip meter and have no route; the owner approved starting
     * clean). Every other table is untouched.
     */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `rides`")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `rides` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `ownerId` TEXT NOT NULL,
                    `startedAt` INTEGER NOT NULL,
                    `endedAt` INTEGER NOT NULL,
                    `distanceMeters` INTEGER NOT NULL,
                    `durationMillis` INTEGER NOT NULL,
                    `topSpeedKmh` INTEGER NOT NULL,
                    `avgSpeedKmh` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `vehicleId` TEXT NOT NULL,
                    `route` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `keepState` TEXT NOT NULL,
                    `expiresAt` INTEGER,
                    `cloudState` INTEGER NOT NULL,
                    `cloudSlot` TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_rides_ownerId` ON `rides` (`ownerId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_rides_keepState_expiresAt` ON `rides` (`keepState`, `expiresAt`)"
            )
        }
    }

    @Provides
    @Singleton
    fun provideRideDatabase(@ApplicationContext context: Context): RideDatabase =
        Room.databaseBuilder(context, RideDatabase::class.java, "rideconnectx.db")
            // Schema is v9; when it changes, add a real Migration here rather
            // than destroying the rider's history. A Migration defined but not
            // listed here crashes every upgrading install on first open.
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            )
            .build()

    @Provides
    fun provideRideDao(db: RideDatabase): RideDao = db.rideDao()

    @Provides
    fun provideNotificationDao(db: RideDatabase): NotificationDao = db.notificationDao()

    @Provides
    fun provideServiceRecordDao(db: RideDatabase): ServiceRecordDao = db.serviceRecordDao()

    @Provides
    fun provideEmergencyContactDao(db: RideDatabase): EmergencyContactDao = db.emergencyContactDao()

    @Provides
    fun provideFuelSampleDao(db: RideDatabase): FuelSampleDao = db.fuelSampleDao()

    @Provides
    fun provideServiceTaskDao(db: RideDatabase): ServiceTaskDao = db.serviceTaskDao()

    @Provides
    fun provideRefuelDao(db: RideDatabase): RefuelDao = db.refuelDao()
}
