package com.planaday.nativeapp.core.model

import kotlinx.serialization.Serializable

/**
 * PD-008: Typed named export artifact so filename, MIME type, and payload bytes can NEVER be swapped.
 */
@Serializable
data class ExportArtifact(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
    val fidelityWarnings: List<String> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExportArtifact) return false
        return filename == other.filename &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes) &&
            fidelityWarnings == other.fidelityWarnings
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + fidelityWarnings.hashCode()
        return result
    }
}

@Serializable
data class SheetCellInput(
    val raw: String,
    val numberFormat: String? = null,
)

@Serializable
data class WorksheetModel(
    val id: String,
    val name: String,
    val rowCount: Int = 100,
    val colCount: Int = 26,
    val namedRanges: Map<String, String> = emptyMap(),
    val cells: Map<String, SheetCellInput> = emptyMap(), // Sparse map e.g. "A1" -> input
)

@Serializable
data class SpreadsheetWorkbook(
    val id: String,
    val workspaceId: String,
    val title: String,
    val sheets: List<WorksheetModel>,
    val rev: Long = 1L,
)

@Serializable
enum class SlideObjectKind {
    TEXT,
    IMAGE,
    TABLE,
    CODE,
    SHAPE,
    CHART_SNAPSHOT,
}

@Serializable
data class SlideObject(
    val id: String,
    val kind: SlideObjectKind,
    val xPct: Double,
    val yPct: Double,
    val widthPct: Double,
    val heightPct: Double,
    val rotationDeg: Double = 0.0,
    val opacity: Double = 1.0,
    val zIndex: Int = 0,
    val text: String = "",
    val blobSha256: String? = null,
    val tableRows: List<List<String>> = emptyList(),
    val codeLanguage: String? = null,
    val snapshotId: String? = null,
    val bgColorHex: String? = null,
    val textColorHex: String? = null,
)

@Serializable
data class SlideModel(
    val id: String,
    val order: Int,
    val title: String = "",
    val transition: String = "none", // none | fade | slide | zoom
    val speakerNotes: String = "",
    val backgroundHex: String = "#FFFFFF",
    val objects: List<SlideObject> = emptyList(),
)

@Serializable
data class PresentationDeck(
    val id: String,
    val workspaceId: String,
    val title: String,
    val aspectRatio: String = "16:9", // 16:9 | 4:3
    val themeId: String = "clean_editorial",
    val slides: List<SlideModel> = emptyList(),
    val rev: Long = 1L,
)

@Serializable
data class KanbanColumnDef(
    val id: String,
    val title: String,
    val wipLimit: Int? = null,
)

@Serializable
data class KanbanBoard(
    val id: String,
    val workspaceId: String,
    val title: String,
    val liveTasksBoard: Boolean = false,
    val columns: List<KanbanColumnDef>,
    val rev: Long = 1L,
)

@Serializable
data class ProjectItem(
    val id: String,
    val workspaceId: String,
    val parentProjectId: String? = null,
    val title: String,
    val description: String = "",
    val startDateIso: String? = null,
    val targetDateIso: String? = null,
    val dependsOnProjectIds: List<String> = emptyList(),
    val status: RecordStatus = RecordStatus.ACTIVE,
    val rev: Long = 1L,
)

@Serializable
data class BookProject(
    val id: String,
    val workspaceId: String,
    val seriesName: String? = null,
    val title: String,
    val synopsis: String = "",
    val targetWordCount: Int = 80_000,
    val rev: Long = 1L,
)

@Serializable
data class BookChapter(
    val id: String,
    val bookId: String,
    val workspaceId: String,
    val chapterNumber: Int,
    val title: String,
    val summary: String = "",
    val manuscriptText: String = "",
    val linkedEntityIds: List<String> = emptyList(),
    val rev: Long = 1L,
)

@Serializable
data class GameProject(
    val id: String,
    val workspaceId: String,
    val title: String,
    val genre: String = "",
    val overview: String = "",
    val rev: Long = 1L,
)

@Serializable
data class CodeFile(
    val path: String,
    val language: String,
    val content: String,
)

@Serializable
data class CodeProject(
    val id: String,
    val workspaceId: String,
    val title: String,
    val primaryLanguage: String,
    val files: List<CodeFile> = emptyList(),
    val rev: Long = 1L,
)

@Serializable
data class PhotoEditRecipe(
    val cropX: Double = 0.0,
    val cropY: Double = 0.0,
    val cropW: Double = 1.0,
    val cropH: Double = 1.0,
    val rotationDeg: Int = 0,
    val brightnessDelta: Double = 0.0,
    val contrastFactor: Double = 1.0,
    val annotationsJson: String = "[]",
)

@Serializable
data class PhotoAsset(
    val id: String,
    val workspaceId: String,
    val originalBlobSha256: String,
    val title: String,
    val album: String = "All Photos",
    val caption: String = "",
    val editRecipe: PhotoEditRecipe = PhotoEditRecipe(),
    val rev: Long = 1L,
    val createdAtUtc: String,
)
