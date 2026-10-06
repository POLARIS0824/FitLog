package com.example.fitlog.data.index

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MemorySourceIndexStore : SourceIndexStore {
    private val state = MutableStateFlow<Map<String, SourceIndexSnapshot>>(emptyMap())
    var fail = false
    override fun observe(vaultId: String) = state.map { it[vaultId] ?: SourceIndexSnapshot(emptyList(), null) }
    override suspend fun sources(vaultId: String) = state.value[vaultId]?.sources.orEmpty()
    override suspend fun commit(sources: List<IndexedSource>, scan: IndexedScan?) {
        if (fail) throw java.io.IOException()
        val next = state.value.toMutableMap()
        (sources.map { it.vaultId } + listOfNotNull(scan?.vaultId)).distinct().forEach { vaultId ->
            val old = next[vaultId] ?: SourceIndexSnapshot(emptyList(), null)
            val rows = old.sources.associateBy { it.uri }.toMutableMap()
            sources.filter { it.vaultId == vaultId }.forEach { rows[it.uri] = it }
            next[vaultId] = SourceIndexSnapshot(rows.values.toList(), scan?.takeIf { it.vaultId == vaultId } ?: old.scan)
        }
        state.value = next
    }

}
