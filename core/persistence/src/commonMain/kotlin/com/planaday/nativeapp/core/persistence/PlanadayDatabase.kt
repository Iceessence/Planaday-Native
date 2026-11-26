package com.planaday.nativeapp.core.persistence

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Canonical Room KMP + Bundled SQLite Schema v2 (ADR-002, ADR-004, Owner Decision O-004, Update 2 §6).
 * Always initializes completely empty new databases — zero legacy Planaday DB import.
 * Supports clean forward migrations for newly created Planaday Native data (v1 -> v2).
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
    version = 2,
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

object PlanadayMigrations {
    /** Real additive v1 -> v2 migration adding indexed calendar date-range & keyset pagination indexes. */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE record_index ADD COLUMN schedule_start_iso TEXT")
            connection.execSQL("ALTER TABLE record_index ADD COLUMN schedule_end_iso TEXT")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_record_index_workspace_id_kind_deleted_at_utc_updated_at_utc_id " +
                    "ON record_index (workspace_id, kind, deleted_at_utc, updated_at_utc, id)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS index_record_index_workspace_id_kind_schedule_start_iso_schedule_end_iso " +
                    "ON record_index (workspace_id, kind, schedule_start_iso, schedule_end_iso)"
            )
        }
    }
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
