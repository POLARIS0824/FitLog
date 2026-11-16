package com.example.fitlog.model.source

import java.util.concurrent.atomic.AtomicLong

data class MarkdownFile(val uri: String, val name: String, val path: String, val writable: Boolean,
    val directory: String? = null, val lastModified: Long? = null, val size: Long? = null)
data class MarkdownSnapshot(val file: MarkdownFile, val text: String, val fingerprint: String, val bom: Boolean,
    /** Logical read order, allocated before I/O; distinct even within the same millisecond. */
    val readAt: Long = nextMarkdownReadOrder())
data class MarkdownScan(val files: List<MarkdownFile>, val partial: Boolean)

private val lastMarkdownReadOrder = AtomicLong()

/** A process-wide order that continues increasing if the wall clock repeats or moves backwards. */
internal fun nextMarkdownReadOrder(now: Long = System.currentTimeMillis()): Long =
    lastMarkdownReadOrder.updateAndGet { previous -> maxOf(now, previous + 1) }
