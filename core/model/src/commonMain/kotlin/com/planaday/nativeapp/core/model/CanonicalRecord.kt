package com.planaday.nativeapp.core.model

import kotlinx.serialization.Serializable

/**
 * Exhaustive enumeration of every canonical Planaday Native record family
 * covering all 88 historical record types plus N-series first-release records.
 */
@Serializable
enum class RecordKind {
    // Planning & Scheduling
    TASK,
    CHORE,
    HABIT,
    GOAL,
    ROUTINE,
    EVENT,
    TIMEBLOCK,
    CALENDAR_LAYER,

    // Mind, Knowledge & Time
    NOTE,
    NOTEBOOK,
    THOUGHT,
    DIARY,
    LIFE_EVENT,
    COUNTDOWN,
    ALARM,
    TIMER,
    INTERVAL_PRESET,
    TIME_LEDGER,

    // Kitchen
    RECIPE,
    PANTRY_LOT,
    PANTRY_ZONE,
    MEAL_PLAN,
    FOOD_LOG,
    GROCERY_ITEM,

    // Personal Life, Household, Wellness & Finance
    PERSON,
    HOUSEHOLD_MEMBER,
    MEDICATION,
    WORKOUT,
    WELLNESS_LOG,
    BILL,
    BUDGET_ENVELOPE,
    EXPENSE,
    WARRANTY,
    WISHLIST_ITEM,
    REVIEW_CARD,
    MEDIA_ITEM,
    HOBBY_PROJECT,
    TRIP,
    PACKING_TEMPLATE,
    PARTY,
    GIFT,
    HOME_ASSET,
    VEHICLE,
    PET_PLANT,
    STORAGE_ITEM,

    // Customization, Planner & Decoration
    CUSTOM_SPACE,
    DECOR_ITEM,
    CUSTOM_STICKER,
    AUTOMATION_RULE,
    SPREAD_TEMPLATE,
    SAVED_VIEW,
    PERIOD_REFLECTION,
    TRIAGE_ITEM,
    CONTEXT_MODE,
    QUICK_CAPTURE,
    PLANNER_BOOK,
    PLANNER_PAGE,
    PLANNER_BOOKMARK,

    // Laboratory Operations (15 Submodules + Workbook)
    LAB_SAMPLE,
    LAB_SUBCONTRACT,
    LAB_INVENTORY,
    LAB_ORDER,
    LAB_BUDGET,
    LAB_PRICE_ENTRY,
    LAB_PRICE_TEMPLATE,
    LAB_RESEARCH,
    LAB_EMAIL_DRAFT,
    LAB_TASK,
    LAB_MEETING,
    LAB_CALIBRATION,
    LAB_WET_NIR,
    LAB_WORK_HOUR,
    LAB_QMS_ITEM,
    LAB_WORKSHEET,

    // Mathematics, Office, Projects, Creative & Files
    CALC_SAVED,
    SPREADSHEET,
    PRESENTATION,
    PROJECT,
    CUSTOM_SCHEMA,
    CUSTOM_ENTITY,
    GAME_PROJECT,
    GAME_ENTITY,
    BOOK_PROJECT,
    BOOK_CHAPTER,
    BOOK_ENTITY,
    KANBAN_BOARD,
    KANBAN_CARD,
    WHITEBOARD,
    CODE_PROJECT,
    CODE_SNIPPET,
    PHOTO_ASSET,
    FILE_FOLDER,
    DOCUMENT_REVIEW,

    // First-release N-series & PF-40/41 records
    DECISION_JOURNAL,
    EVIDENCE_SNAPSHOT,
    CUSTOM_TAB,
}

@Serializable
enum class RecordStatus {
    ACTIVE,
    DONE,
    PAUSED,
    ARCHIVED,
    CANCELED,
}

@Serializable
enum class Priority(val rank: Int) {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    URGENT(4),
}

@Serializable
enum class EnergyLevel {
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
enum class RelationKind {
    MENTIONS,
    ATTACHED_NOTE,
    SCHEDULED_FROM,
    PERSON,
    RECIPE,
    SUPPLY,
    PARENT,
    DEPENDS_ON,
    EXPENSE,
    ASSET,
    PROMOTED_FROM,
    EVIDENCE_FOR,
    OUTCOME_OF,
    DERIVED_FROM,
}

@Serializable
data class RecordRelation(
    val id: String,
    val workspaceId: String,
    val sourceId: String,
    val sourceKind: RecordKind,
    val targetId: String,
    val targetKind: RecordKind,
    val relationKind: RelationKind,
    val sortOrder: Int = 0,
    val label: String? = null,
    val createdAtUtc: String,
)

@Serializable
data class CanonicalRecord(
    val id: String,
    val workspaceId: String,
    val kind: RecordKind,
    val title: String,
    val status: RecordStatus = RecordStatus.ACTIVE,
    val rev: Long = 1L,
    val schemaVersion: Int = BuildIdentity.CURRENT_CLIENT_SCHEMA_VERSION,
    val createdAtUtc: String,
    val updatedAtUtc: String,
    val deletedAtUtc: String? = null,
    val deviceId: String,
    val ownerProfileId: String? = null,
    val sharedWithAll: Boolean = false,
    val sharedWithProfileIds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val summaryText: String = "",
    val payloadJson: String = "{}",
    val extraFieldsJson: String = "{}", // Forward-compatible unknown field preservation
    val isSyntheticDemo: Boolean = false,
)

@Serializable
data class RevisionSnapshot(
    val id: String,
    val workspaceId: String,
    val recordId: String,
    val recordKind: RecordKind,
    val rev: Long,
    val title: String,
    val contentSnapshot: String,
    val checkpointLabel: String? = null,
    val createdAtUtc: String,
    val deviceId: String,
    val isImmutableEvidence: Boolean = false,
)

@Serializable
data class DraftRecord(
    val sessionId: String,
    val workspaceId: String,
    val recordId: String,
    val recordKind: RecordKind,
    val titleDraft: String,
    val bodyDraft: String,
    val baseRev: Long,
    val updatedAtUtc: String,
    val dirty: Boolean = true,
)

@Serializable
data class EvidenceSnapshot(
    val id: String,
    val workspaceId: String,
    val sourceRecordId: String,
    val sourceKind: RecordKind,
    val sourceRev: Long,
    val title: String,
    val contentJson: String,
    val contentSha256: String,
    val provenanceNote: String,
    val createdAtUtc: String,
)

@Serializable
data class AttachmentMeta(
    val sha256: String,
    val mimeType: String,
    val byteSize: Long,
    val originalFilename: String,
    val createdAtUtc: String,
    val localStorageRelPath: String,
    val thumbnailRelPath: String? = null,
)

@Serializable
data class AttachmentRef(
    val id: String,
    val workspaceId: String,
    val recordId: String,
    val sha256: String,
    val role: String = "attachment",
    val createdAtUtc: String,
)

@Serializable
enum class CustomFieldType {
    TEXT,
    NUMBER,
    DECIMAL,
    BOOLEAN,
    DATE,
    SINGLE_SELECT,
    MULTI_SELECT,
    RECORD_LINK,
    COMPUTED_FORMULA,
}

@Serializable
data class CustomFieldDef(
    val key: String,
    val label: String,
    val fieldType: CustomFieldType,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val formulaExpression: String? = null,
    val minNumber: Double? = null,
    val maxNumber: Double? = null,
)

@Serializable
data class CustomSchemaDef(
    val id: String,
    val workspaceId: String,
    val name: String,
    val schemaVersion: Int = 1,
    val fields: List<CustomFieldDef>,
    val defaultView: String = "table", // table | list | gallery | kanban | calendar
)

@Serializable
data class DecisionJournalEntry(
    val id: String,
    val workspaceId: String,
    val title: String,
    val context: String,
    val optionsConsidered: List<String>,
    val chosenOption: String,
    val expectedOutcome: String,
    val reviewDateIso: String? = null,
    val actualOutcome: String? = null,
    val linkedRecordIds: List<String> = emptyList(),
    val createdAtUtc: String,
)
