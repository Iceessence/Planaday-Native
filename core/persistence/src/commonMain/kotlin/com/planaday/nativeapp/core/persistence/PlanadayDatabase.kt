package com.planaday.nativeapp.core.persistence

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Canonical Room KMP + Bundled SQLite Schema v1 (ADR-002, ADR-004, Owner Decision O-004).
 * Always initializes completely empty new databases — zero legacy Planaday DB import.
 */
@Database(
    entities = [
        WorkspaceEntity::class,
        CanonicalRecordEntity::class,
        RelationEntity::class,
        DraftEntity::class,
        RevisionEntity::class,
        EvidenceSnapshotEntity::class,
        AttachmentEntity::class,
        AttachmentRefEntity::class,
        PlannerBookmarkEntity::class,
        OutboxOpEntity::class,
        SyncConflictEntity::class,
        SyncCursorEntity::class,
        BlobTransferReceiptEntity::class,
        AutomationRunEntity::class,
        AppPreferenceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(PlanadayDatabaseConstructor::class)
abstract class PlanadayDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun recordDao(): RecordDao
    abstract fun relationDao(): RelationDao
    abstract fun draftAndRevisionDao(): DraftAndRevisionDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun plannerBookmarkDao(): PlannerBookmarkDao
    abstract fun syncDao(): SyncDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object PlanadayDatabaseConstructor : RoomDatabaseConstructor<PlanadayDatabase> {
    override fun initialize(): PlanadayDatabase
}

/**
 * Configures WAL mode, foreign keys, busy_timeout, and SQLite FTS5 full-text search index + triggers
 * on fresh bundled SQLite connections.
 */
object PlanadaySqlitePragmasAndFts : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        super.onCreate(connection)
        ensureFts5Objects(connection)
    }

    override fun onOpen(connection: SQLiteConnection) {
        super.onOpen(connection)
        connection.execSQL("PRAGMA foreign_keys = ON;")
        connection.execSQL("PRAGMA busy_timeout = 5000;")
        ensureFts5Objects(connection)
    }

    fun ensureFts5Objects(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS record_fts USING fts5(
                record_id UNINDEXED,
                workspace_id UNINDEXED,
                kind UNINDEXED,
                title,
                summary_text,
                tags_text,
                tokenize = 'unicode61'
            );
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_record_index_ai AFTER INSERT ON record_index
            WHEN NEW.deleted_at_utc IS NULL
            BEGIN
                INSERT INTO record_fts(record_id, workspace_id, kind, title, summary_text, tags_text)
                VALUES (NEW.id, NEW.workspace_id, NEW.kind, NEW.title, NEW.summary_text, NEW.tags_json);
            END;
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_record_index_au AFTER UPDATE ON record_index
            BEGIN
                DELETE FROM record_fts WHERE record_id = OLD.id;
                INSERT INTO record_fts(record_id, workspace_id, kind, title, summary_text, tags_text)
                SELECT NEW.id, NEW.workspace_id, NEW.kind, NEW.title, NEW.summary_text, NEW.tags_json
                WHERE NEW.deleted_at_utc IS NULL;
            END;
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_record_index_ad AFTER DELETE ON record_index
            BEGIN
                DELETE FROM record_fts WHERE record_id = OLD.id;
            END;
            """.trimIndent()
        )
    }
}
