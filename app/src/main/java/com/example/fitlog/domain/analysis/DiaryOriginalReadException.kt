package com.example.fitlog.domain.analysis

import java.io.IOException

internal class DiaryOriginalReadException(cause: Exception) : IOException("Cannot read diary original", cause)
