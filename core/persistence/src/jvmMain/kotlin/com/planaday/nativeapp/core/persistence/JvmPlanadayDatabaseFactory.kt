package com.planaday.nativeapp.core.persistence

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * JVM/Windows Desktop Room KMP + Bundled SQLite Database Builder (ADR-002, O-004).
 * Always creates an independent fresh database file (e.g., %LOCALAPPDATA%\PlanadayNative\planaday_native_v1.db)
 * with BundledSQLiteDriver (WAL + FTS5 + FK enabled) — never opens or reads any legacy Planaday database!
 */
object JvmPlanadayDatabaseFactory {
    fun createOnDisk(dbFile: File): PlanadayDatabase {
        dbFile.parentFile?.mkdirs()
        return Room.databaseBuilder<PlanadayDatabase>(
            name = dbFile.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(PlanadayMigrations.MIGRATION_1_2)
            .addCallback(PlanadaySqlitePragmasAndFts)
            .build()
    }

    fun createInMemory(): PlanadayDatabase {
        return Room.inMemoryDatabaseBuilder<PlanadayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(PlanadaySqlitePragmasAndFts)
            .build()
    }

    fun defaultWindowsDataDir(): File {
        val localAppData = System.getenv("LOCALAPPDATA")
            ?: File(System.getProperty("user.home"), ".local/share").absolutePath
        return File(localAppData, "PlanadayNative")
    }
}
