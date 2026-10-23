package com.example.fitlog.data.index

import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.IndexedSource
import org.junit.Assert.*
import org.junit.Test

class SourceIndexMappingTest {
    @Test fun persistenceMappingKeepsTheObservationOrderAndPartialScanState() {
        val vaultId = "00000000-0000-0000-0000-000000000001"
        val source = IndexedSource(vaultId, "content://notes/1", "training.md", "diary/training.md",
            "content://notes", false, IndexedSource.READ_FAILED, "a".repeat(64), 1, 123)
        val scan = IndexedScan(vaultId, IndexedScan.PARTIAL, 456)
        val observed = source.toRow().toSource()
        assertEquals(source, observed)
        assertEquals(scan, scan.toRow().toScan())
        assertEquals(123L, observed.observedAt)
        assertFalse(observed.file().writable)
        assertEquals("diary/training.md", observed.file().path)
    }
}
