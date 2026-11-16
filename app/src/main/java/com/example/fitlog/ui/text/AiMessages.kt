package com.example.fitlog.ui.text

import com.example.fitlog.R
import com.example.fitlog.data.ai.transport.AiRequestFailure
import com.example.fitlog.model.ai.AiConfigurationFailure

/** Protocol/configuration failures become user-facing strings in one place, not in feature VMs. */
internal fun aiConfigurationMessage(reason: AiConfigurationFailure): Int = when (reason) {
    AiConfigurationFailure.NOT_CONFIGURED -> R.string.ai_not_configured
    AiConfigurationFailure.KEY_UNREADABLE -> R.string.ai_key_unreadable
    AiConfigurationFailure.INVALID_CONFIGURATION -> R.string.ai_invalid_credentials
    AiConfigurationFailure.STORAGE -> R.string.ai_settings_read_failed
}

internal fun aiRequestMessage(reason: AiRequestFailure): Int = when (reason) {
    AiRequestFailure.AUTHENTICATION_ERROR -> R.string.detail_failure_authentication
    AiRequestFailure.PERMISSION_DENIED -> R.string.detail_failure_permission
    AiRequestFailure.RATE_LIMITED -> R.string.detail_failure_rate_limit
    AiRequestFailure.SERVICE_UNAVAILABLE -> R.string.detail_failure_service
    AiRequestFailure.TIMEOUT -> R.string.detail_failure_timeout
    AiRequestFailure.NETWORK_ERROR -> R.string.detail_failure_network
    else -> R.string.ai_request_failed
}
