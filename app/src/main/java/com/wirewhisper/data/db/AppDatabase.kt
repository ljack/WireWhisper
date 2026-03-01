package com.wirewhisper.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FlowEntity::class, GeoCacheEntity::class, BlockRuleEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun flowDao(): FlowDao
    abstract fun geoCacheDao(): GeoCacheDao
    abstract fun blockRuleDao(): BlockRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `geo_cache` (
                        `ip` TEXT NOT NULL,
                        `countryCode` TEXT NOT NULL,
                        `countryName` TEXT,
                        `city` TEXT,
                        `asn` TEXT,
                        `org` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ip`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `block_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `hostname` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_block_rules_packageName` ON `block_rules` (`packageName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_block_rules_hostname` ON `block_rules` (`hostname`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rebuild table: packageName NOT NULL → nullable, add countryCode column
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `block_rules_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT,
                        `hostname` TEXT,
                        `countryCode` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `block_rules_new` (`id`, `packageName`, `hostname`, `createdAt`)
                    SELECT `id`, `packageName`, `hostname`, `createdAt` FROM `block_rules`
                """.trimIndent())
                db.execSQL("DROP TABLE `block_rules`")
                db.execSQL("ALTER TABLE `block_rules_new` RENAME TO `block_rules`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_block_rules_packageName` ON `block_rules` (`packageName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_block_rules_hostname` ON `block_rules` (`hostname`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_block_rules_countryCode` ON `block_rules` (`countryCode`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wirewhisper.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
