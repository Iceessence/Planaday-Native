package com.planaday.nativeapp.core.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.planaday.nativeapp.core.model.BuildIdentity
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Android Room KMP + Bundled SQLite Database Builder (ADR-002, O-004).
 * Uses identical BundledSQLiteDriver across API 29..36 so OEM SQLite differences never break WAL or FTS5.
 */
object AndroidPlanadayDatabaseFactory {
    fun create(context: Context, dbFilename: String = BuildIdentity.CLIENT_DB_NAME): PlanadayDatabase {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(dbFilename)
        dbFile.parentFile?.mkdirs()
        return Room.databaseBuilder<PlanadayDatabase>(
            context = appContext,
            name = dbFile.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(PlanadaySqlitePragmasAndFts)
            .build()
    }

    fun defaultBlobDir(context: Context): File = File(context.applicationContext.filesDir, "blobs_v1")
}
