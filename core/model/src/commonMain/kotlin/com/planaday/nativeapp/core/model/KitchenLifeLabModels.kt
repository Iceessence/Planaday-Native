package com.planaday.nativeapp.core.model

import kotlinx.serialization.Serializable

/**
 * Exact decimal representation for currency and laboratory precision (zero binary float money math).
 * Uses canonical string decimal form (e.g. "125.50") validated by exact decimal arithmetic in core:math / core:domain.
 */
@Serializable
data class DecimalAmount(
    val value: String,
    val unitOrCurrency: String = "USD",
)

@Serializable
data class RecipeIngredient(
    val id: String,
    val quantity: Double,
    val unit: String,
    val name: String,
    val canonicalName: String,
    val aisle: String = "Pantry",
    val optional: Boolean = false,
)

@Serializable
data class RecipeStep(
    val order: Int,
    val text: String,
    val durationMin: Int? = null,
)

@Serializable
data class RecipeItem(
    val id: String,
    val workspaceId: String,
    val title: String,
    val servings: Double = 4.0,
    val prepMin: Int = 15,
    val cookMin: Int = 30,
    val cuisine: String? = null,
    val sourceUrl: String? = null,
    val sourceProvenance: String? = null,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList(),
    val heroBlobSha256: String? = null,
    val rev: Long = 1L,
)

@Serializable
data class PantryLot(
    val id: String,
    val workspaceId: String,
    val ingredientName: String,
    val canonicalName: String,
    val quantity: Double,
    val unit: String,
    val zone: String = "Dry Pantry",
    val barcode: String? = null,
    val purchaseDateIso: String? = null,
    val expiryDateIso: String? = null,
    val parLevel: Double? = null,
    val rev: Long = 1L,
)

@Serializable
data class MealPlanItem(
    val id: String,
    val workspaceId: String,
    val dateIso: String,
    val slot: String, // breakfast | lunch | dinner | snack
    val recipeId: String? = null,
    val label: String,
    val servings: Double = 2.0,
)

@Serializable
data class FoodLogItem(
    val id: String,
    val workspaceId: String,
    val dateIso: String,
    val timeLocal: String? = null,
    val mealCategory: String,
    val foodName: String,
    val recipeId: String? = null,
    val portionQty: Double? = null,
    val portionUnit: String? = null,
    val photoBlobSha256: String? = null,
    val notes: String = "",
)

@Serializable
data class GroceryItem(
    val id: String,
    val workspaceId: String,
    val canonicalName: String,
    val displayName: String,
    val quantity: Double,
    val unit: String,
    val aisle: String = "General",
    val store: String = "Default",
    val checked: Boolean = false,
    val unitPrice: DecimalAmount? = null,
    val targetZone: String = "Dry Pantry",
    val expiryDateIso: String? = null,
    val linkedExpenseId: String? = null,
)

@Serializable
data class BudgetEnvelope(
    val id: String,
    val workspaceId: String,
    val name: String,
    val category: String,
    val limitAmount: DecimalAmount,
    val period: String = "monthly",
)

@Serializable
data class ExpenseItem(
    val id: String,
    val workspaceId: String,
    val dateIso: String,
    val description: String,
    val amount: DecimalAmount,
    val category: String,
    val envelopeId: String? = null,
    val receiptBlobSha256: String? = null,
    val sourceRecordId: String? = null,
)

// --- Laboratory Operations (Operational Management Aid — NOT accredited LIMS) ---

@Serializable
data class LabCustodyEvent(
    val id: String,
    val timestampUtc: String,
    val actor: String,
    val action: String, // RECEIVED | TRANSFERRED | PREPARED | ANALYZED | REVIEWED | DISPOSED
    val location: String,
    val notes: String = "",
)

@Serializable
data class LabSample(
    val id: String,
    val workspaceId: String,
    val sampleCode: String,
    val clientName: String,
    val commodity: String,
    val analysisMethods: List<String>,
    val receivedDateIso: String,
    val dueDateIso: String,
    val status: String = "received", // received | in_progress | subcontracted | completed | archived
    val subcontractLab: String? = null,
    val custodyEvents: List<LabCustodyEvent> = emptyList(),
    val rev: Long = 1L,
)

@Serializable
data class LabCorrectionEntry(
    val id: String,
    val timestampUtc: String,
    val actor: String,
    val reason: String,
    val correctedValue: String,
    val unit: String,
)

/**
 * Immutable raw laboratory calibration / QC record:
 * original raw measurements are NEVER overwritten; corrections are append-only!
 */
@Serializable
data class LabCalibrationRecord(
    val id: String,
    val workspaceId: String,
    val instrumentId: String,
    val checkType: String, // calibration | balance_weight | oven_qc | pipette_check | maintenance
    val performedAtUtc: String,
    val nextDueDateIso: String,
    val analyst: String,
    val rawMeasurementValue: String,
    val referenceStandardValue: String,
    val unit: String,
    val passed: Boolean,
    val corrections: List<LabCorrectionEntry> = emptyList(),
    val rev: Long = 1L,
)

@Serializable
data class LabWetNirPair(
    val id: String,
    val workspaceId: String,
    val sampleId: String,
    val commodity: String,
    val analysis: String,
    val wetMethod: String,
    val wetResult: Double,
    val wetReplicates: List<Double> = emptyList(),
    val nirResult: Double,
    val nirReplicates: List<Double> = emptyList(),
    val unit: String = "%",
    val instrumentId: String,
    val calibrationModel: String,
    val dateTestedIso: String,
    val analyst: String,
    val excluded: Boolean = false,
    val excludeReason: String? = null,
)

@Serializable
data class LabInventoryItem(
    val id: String,
    val workspaceId: String,
    val category: String, // instrument | lab_supply | office_supply | reagent_lot
    val name: String,
    val lotNumber: String? = null,
    val quantityOnHand: Double,
    val reorderThreshold: Double,
    val unit: String,
    val vendorName: String? = null,
    val unitPrice: DecimalAmount? = null,
    val expiryDateIso: String? = null,
)

@Serializable
data class LabOrder(
    val id: String,
    val workspaceId: String,
    val inventoryItemId: String?,
    val vendorName: String,
    val itemDescription: String,
    val quantity: Double,
    val totalCost: DecimalAmount,
    val budgetId: String?,
    val orderDateIso: String,
    val status: String = "ordered", // draft | approved | ordered | received
)

@Serializable
data class LabMeeting(
    val id: String,
    val workspaceId: String,
    val title: String,
    val meetingKind: String, // weekly_analyst | monthly_safety | method_review | general
    val dateIso: String,
    val attendees: List<String>,
    val notes: String,
    val actionItems: List<String>,
    val promotedTaskIds: List<String> = emptyList(),
)
