package com.example.fitlog.domain.ai

import com.example.fitlog.model.ai.AiConfigurationFailure
import java.io.IOException

class AiConfigurationException(val reason: AiConfigurationFailure, cause: Exception? = null) :
    IOException("AI configuration: ${reason.name}", cause)
