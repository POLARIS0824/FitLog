package com.example.fitlog.data.index

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MemorySourceIndexStore : SourceIndexStore {
    private val state = MutableStateFlow<Map<String, SourceIndexSnapshot>>(emptyMap())
    var fail = false
    override fun observe(vault: String) = state.map { it[vault] ?: SourceIndexSnapshot(emptyList(), null) }
    override suspend fun sources(vault: String) = state.value[vault]?.sources.orEmpty()
    override suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan?) {
        if (fail) throw java.io.IOException()
        val next = state.value.toMutableMap()
        (sources.map { it.vaultId } + listOfNotNull(scan?.vaultId)).distinct().forEach { vault ->
            val old = next[vault] ?: SourceIndexSnapshot(emptyList(), null)
            val rows = old.sources.associateBy { it.uri }.toMutableMap()
            sources.filter { it.vaultId == vault }.forEach { rows[it.uri] = it }
            next[vault] = SourceIndexSnapshot(rows.values.toList(), scan?.takeIf { it.vaultId == vault } ?: old.scan)
        }
        state.value = next
    }

    override suspend fun migrateVault(legacyUri: String, vaultId: String) {
        if (fail) throw java.io.IOException()
        if (legacyUri == vaultId) return
        val old = state.value[legacyUri] ?: return
        val current = state.value[vaultId]
        val rows = (old.sources.map { it.copy(vaultId = vaultId) } + current?.sources.orEmpty()).associateBy { it.uri }
        state.value = state.value - legacyUri + (vaultId to SourceIndexSnapshot(rows.values.toList(),
            current?.scan ?: old.scan?.copy(vaultId = vaultId)))
    }
}
