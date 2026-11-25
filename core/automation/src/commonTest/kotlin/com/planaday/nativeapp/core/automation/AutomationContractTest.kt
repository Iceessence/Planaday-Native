package com.planaday.nativeapp.core.automation

import com.planaday.nativeapp.core.model.RecordKind
import kotlin.test.*

class AutomationContractTest {
    @Test
    fun idempotentReplayAndCycleGuardAndApprovalGate() {
        val engine = AutomationEngine(maxChainDepth = 3)
        val rules = listOf(
            AutomationRuleDef("rule1", "ws1", "Auto Grocery", "CHORE_COMPLETED", "cleaning", "ADD_GROCERY_ITEM", "Dish soap", requiresHumanApproval = false),
            AutomationRuleDef("rule2", "ws1", "Sensitive Export", "CHORE_COMPLETED", null, "EXPORT_AUDIT", "Archive", requiresHumanApproval = true),
        )
        val event = AutomationDomainEvent("evt_123", "ws1", "CHORE_COMPLETED", "ch1", RecordKind.CHORE, setOf("cleaning"), depth = 0)
        val previews = engine.preview(rules, event)
        assertEquals(2, previews.size)

        val ran = mutableListOf<String>()
        engine.executeApproved(previews, approvedKeys = emptySet()) { ran.add(it.ruleId) }
        assertEquals(listOf("rule1"), ran) // rule2 waited for human approval

        // Replaying identical event after sync MUST NOT duplicate execution (INT-015)!
        val replayPreviews = engine.preview(rules, event)
        assertEquals(listOf("rule2"), replayPreviews.map { it.ruleId })

        // Cycle depth limit blocks infinite cascades
        val deepEvent = event.copy(eventId = "evt_deep", depth = 3)
        val blocked = engine.preview(rules, deepEvent)
        assertNotNull(blocked.single().blockedReason)
    }
}
