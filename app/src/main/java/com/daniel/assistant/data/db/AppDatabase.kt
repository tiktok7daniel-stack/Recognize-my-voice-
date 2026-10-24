package com.daniel.assistant.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CommandEntity::class, AliasEntity::class, LogEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commands(): CommandDao
    abstract fun aliases(): AliasDao
    abstract fun logs(): LogDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE commands ADD COLUMN autoTime TEXT")
                db.execSQL("ALTER TABLE commands ADD COLUMN autoApp TEXT")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "nabra.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
