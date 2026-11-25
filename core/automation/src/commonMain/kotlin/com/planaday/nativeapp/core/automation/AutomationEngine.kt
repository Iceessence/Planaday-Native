package com.planaday.nativeapp.core.automation

import com.planaday.nativeapp.core.model.RecordKind
import kotlinx.serialization.Serializable

@Serializable
data class AutomationRuleDef(
    val id: String,
    val workspaceId: String,
    val name: String,
    val triggerEventKind: String, // e.g. TASK_COMPLETED, CHORE_COMPLETED, LAB_MEETING_SAVED, PANTRY_LOW
    val conditionTagOrField: String? = null,
    val actionKind: String, // CREATE_FOLLOWUP_TASK | ADD_GROCERY_ITEM | SEND_REMINDER | LOG_JOURNAL
    val actionTargetLabel: String,
    val requiresHumanApproval: Boolean = false,
    val enabled: Boolean = true,
)

@Serializable
data class AutomationDomainEvent(
    val eventId: String, // Deterministic idempotency key preventing sync-replay duplication!
    val workspaceId: String,
    val eventKind: String,
    val sourceRecordId: String,
    val sourceRecordKind: RecordKind,
    val tags: Set<String> = emptySet(),
    val depth: Int = 0, // Cycle guard counter
)

@Serializable
data class AutomationActionPreview(
    val ruleId: String,
    val idempotencyKey: String,
    val actionKind: String,
    val description: String,
    val requiresApproval: Boolean,
    val blockedReason: String? = null,
)

/**
 * Previewable, idempotent, cycle-bounded user automation engine (PF-34, N-035, INT-015):
 * - Prevents duplicate execution on sync replay via deterministic `idempotencyKey = "$ruleId:${event.eventId}"`.
 * - Enforces maximum trigger cascade depth (`MAX_CHAIN_DEPTH = 3`) to prevent infinite loops.
 * - Flags sensitive/irreversible actions for explicit human approval before commit.
 */
class AutomationEngine(private val maxChainDepth: Int = 3) {
    private val executedKeys = HashSet<String>()

    fun preview(
        rules: List<AutomationRuleDef>,
        event: AutomationDomainEvent,
        alreadyExecutedKeys: Set<String> = executedKeys,
    ): List<AutomationActionPreview> {
        if (event.depth >= maxChainDepth) {
            return listOf(
                AutomationActionPreview(
                    ruleId = "CYCLE_GUARD",
                    idempotencyKey = "cycle:${event.eventId}",
                    actionKind = "BLOCKED",
                    description = "Automation chain depth limit ($maxChainDepth) reached",
                    requiresApproval = false,
                    blockedReason = "Cycle guard prevented runaway automation loop",
                )
            )
        }
        return rules
            .filter { it.enabled && it.workspaceId == event.workspaceId && it.triggerEventKind == event.eventKind }
            .filter { it.conditionTagOrField == null || it.conditionTagOrField in event.tags }
            .mapNotNull { rule ->
                val key = "${rule.id}:${event.eventId}"
                if (key in alreadyExecutedKeys) {
                    null // Idempotently skipped on sync replay
                } else {
                    AutomationActionPreview(
                        ruleId = rule.id,
                        idempotencyKey = key,
                        actionKind = rule.actionKind,
                        description = "[${rule.name}] -> ${rule.actionKind}: ${rule.actionTargetLabel}",
                        requiresApproval = rule.requiresHumanApproval,
                    )
                }
            }
    }

    fun executeApproved(
        previews: List<AutomationActionPreview>,
        approvedKeys: Set<String> = emptySet(),
        executor: (AutomationActionPreview) -> Unit,
    ): List<AutomationActionPreview> {
        val executed = mutableListOf<AutomationActionPreview>()
        for (p in previews) {
            if (p.blockedReason != null) continue
            if (p.requiresApproval && p.idempotencyKey !in approvedKeys) continue
            if (executedKeys.add(p.idempotencyKey)) {
                executor(p)
                executed.add(p)
            }
        }
        return executed
    }
}
