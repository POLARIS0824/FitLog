package com.example.fitlog.data.index

import com.example.fitlog.data.vault.MarkdownFile
import com.example.fitlog.model.source.IndexedScan
import com.example.fitlog.model.source.IndexedSource

fun IndexedSource.file() = MarkdownFile(uri, name, path, writable, directory)

internal fun IndexedSourceRow.toSource() = IndexedSource(vaultId, uri, name, path, directory, writable,
    status, contentHash, hashVersion, observedAt)
internal fun IndexedSource.toRow() = IndexedSourceRow(vaultId, uri, name, path, directory, writable,
    status, contentHash, hashVersion, observedAt)
internal fun IndexedScanRow.toScan() = IndexedScan(vaultId, status, completedAt)
internal fun IndexedScan.toRow() = IndexedScanRow(vaultId, status, completedAt)
