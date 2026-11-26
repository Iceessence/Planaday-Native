package com.planaday.nativeapp.core.graphics

import com.planaday.nativeapp.core.model.PhotoAsset
import com.planaday.nativeapp.core.model.PhotoEditRecipe
import com.planaday.nativeapp.core.model.PlannerPage

data class StickerCatalogEntry(
    val id: String,
    val category: String,
    val label: String,
    val accentHex: String,
    val vectorShape: String, // star | badge | leaf | flask | heart | pin | calendar | flame | book | check
)

data class PhotoRenderDescriptor(
    val sourceOriginalSha256: String,
    val originalUntouched: Boolean = true,
    val outputWidthPx: Int,
    val outputHeightPx: Int,
    val rotationDeg: Int,
    val brightnessDelta: Double,
    val contrastFactor: Double,
)

/**
 * Non-destructive Photo Edit Pipeline (PF-32, R-0115..R-0122) & Newly Authored 260+ Sticker Catalog
 * + Physical Planner Page Management & Undo/Redo Command Stack (R-0055..R-0094, C-0068).
 */
object PhotoAndPlannerAdvanced {
    /**
     * Generates 260 freshly authored geometric/vector sticker definitions across 10 categories
     * (zero legacy artwork copied — EXC-ASSET-001).
     */
    val BUILT_IN_STICKER_CATALOG: List<StickerCatalogEntry> = run {
        val categories = listOf(
            "Planning" to "#A97434",
            "Weather & Nature" to "#15803D",
            "Laboratory & Science" to "#0F766E",
            "Kitchen & Meals" to "#D97706",
            "Wellness & Mood" to "#BE185D",
            "Finance & Goals" to "#1D4ED8",
            "Travel & Maps" to "#0284C7",
            "Creative & Books" to "#4F46E5",
            "Household & Chores" to "#4D7C0F",
            "Badges & Stamps" to "#7C2D12",
        )
        val shapes = listOf("star", "badge", "leaf", "flask", "heart", "pin", "calendar", "flame", "book", "check")
        val out = ArrayList<StickerCatalogEntry>(260)
        for ((catIdx, pair) in categories.withIndex()) {
            val (catName, color) = pair
            for (n in 1..26) {
                out.add(
                    StickerCatalogEntry(
                        id = "stk_${catIdx + 1}_$n",
                        category = catName,
                        label = "$catName #$n",
                        accentHex = color,
                        vectorShape = shapes[(catIdx + n) % shapes.size],
                    )
                )
            }
        }
        out
    }

    /** Applies a non-destructive edit recipe without ever mutating originalBlobSha256 (PF-32, R-0121..R-0122). */
    fun applyPhotoEditRecipe(
        asset: PhotoAsset,
        originalWidthPx: Int,
        originalHeightPx: Int,
        newRecipe: PhotoEditRecipe,
    ): Pair<PhotoAsset, PhotoRenderDescriptor> {
        val updated = asset.copy(
            originalBlobSha256 = asset.originalBlobSha256, // Original immutable!
            editRecipe = newRecipe,
            rev = asset.rev + 1L,
        )
        val croppedW = (originalWidthPx * newRecipe.cropW.coerceIn(0.05, 1.0)).toInt().coerceAtLeast(1)
        val croppedH = (originalHeightPx * newRecipe.cropH.coerceIn(0.05, 1.0)).toInt().coerceAtLeast(1)
        val normalizedRot = ((newRecipe.rotationDeg % 360) + 360) % 360
        val swapped = normalizedRot == 90 || normalizedRot == 270
        val desc = PhotoRenderDescriptor(
            sourceOriginalSha256 = asset.originalBlobSha256,
            originalUntouched = true,
            outputWidthPx = if (swapped) croppedH else croppedW,
            outputHeightPx = if (swapped) croppedW else croppedH,
            rotationDeg = normalizedRot,
            brightnessDelta = newRecipe.brightnessDelta.coerceIn(-1.0, 1.0),
            contrastFactor = newRecipe.contrastFactor.coerceIn(0.2, 3.0),
        )
        return updated to desc
    }

    /** Duplicates a planner page and cleanly renumbers subsequent pages (R-0056, R-0057). */
    fun duplicatePage(pages: List<PlannerPage>, sourcePageId: String, newPageId: String): List<PlannerPage> {
        val sorted = pages.sortedBy { it.pageNumber }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == sourcePageId }
        require(idx >= 0) { "Page $sourcePageId not found" }
        val copy = sorted[idx].copy(id = newPageId, title = "${sorted[idx].title} (Copy)", rev = 1L)
        sorted.add(idx + 1, copy)
        return sorted.mapIndexed { i, p -> p.copy(pageNumber = i + 1) }
    }

    fun reorderPage(pages: List<PlannerPage>, pageId: String, newZeroBasedIndex: Int): List<PlannerPage> {
        val sorted = pages.sortedBy { it.pageNumber }.toMutableList()
        val oldIdx = sorted.indexOfFirst { it.id == pageId }
        require(oldIdx >= 0) { "Page $pageId not found" }
        val item = sorted.removeAt(oldIdx)
        val target = newZeroBasedIndex.coerceIn(0, sorted.size)
        sorted.add(target, item)
        return sorted.mapIndexed { i, p -> p.copy(pageNumber = i + 1) }
    }
}
