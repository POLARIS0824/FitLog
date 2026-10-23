package com.example.fitlog.data.analysis.adapter

import java.io.IOException

internal class DiaryOriginalReadException(cause: Exception) : IOException("Cannot read diary original", cause)
