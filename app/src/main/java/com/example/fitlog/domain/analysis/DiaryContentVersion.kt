package com.example.fitlog.domain.analysis

import com.example.fitlog.model.analysis.DiaryContentVersion

fun DiaryContentVersion.Companion.from(input: DiaryParseInput) =
    DiaryContentVersion(input.parseKey.contentHash, input.parseKey.hashVersion)
