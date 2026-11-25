package com.planaday.nativeapp.core.search

import com.planaday.nativeapp.core.model.RecordKind
import com.planaday.nativeapp.core.persistence.PlanadayRepository
import com.planaday.nativeapp.core.persistence.SearchHit

data class SavedSearchFilter(
    val id: String,
    val name: String,
    val workspaceId: String,
    val query: String,
    val allowedKinds: Set<RecordKind> = emptySet(),
)

/**
 * Workspace-ACL-enforced incremental FTS5 search engine (PF-10, N-006, INT-014).
 */
class PlanadaySearchService(private val repository: PlanadayRepository) {
    suspend fun search(
        authorizedWorkspaceId: String,
        query: String,
        kinds: Set<RecordKind> = emptySet(),
        limit: Int = 50,
    ): List<SearchHit> {
        val raw = repository.searchAuthorizedFts5(authorizedWorkspaceId, query, limit = limit * 2)
        val filtered = if (kinds.isEmpty()) raw else raw.filter { it.kind in kinds }
        return filtered.take(limit)
    }
}
