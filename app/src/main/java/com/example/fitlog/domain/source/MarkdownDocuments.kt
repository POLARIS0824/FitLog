package com.example.fitlog.domain.source

import com.example.fitlog.model.source.MarkdownScan
import com.example.fitlog.model.source.MarkdownSnapshot

interface MarkdownDocuments {
    suspend fun scan(vault: String): MarkdownScan
    suspend fun read(uri: String): MarkdownSnapshot
}
