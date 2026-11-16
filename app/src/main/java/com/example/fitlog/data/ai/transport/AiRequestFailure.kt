package com.example.fitlog.data.ai.transport



/**
 * What went wrong during one request against an OpenAI-compatible provider.
 *
 * This is the vocabulary of the protocol layer, shared by every feature that calls a provider
 * (diary extraction today, agent answers next). Feature-specific shape errors are deliberately
 * absent: a diary JSON schema violation belongs to the diary feature, not to the protocol.
 *
 */
internal enum class AiRequestFailure {
    TIMEOUT,
    NETWORK_ERROR,
    AUTHENTICATION_ERROR,
    PERMISSION_DENIED,
    RATE_LIMITED,
    REQUEST_REJECTED,
    SERVICE_UNAVAILABLE,
    EMPTY_RESPONSE,
    INVALID_RESPONSE,
    TRUNCATED_RESPONSE,
    MODEL_REFUSAL,
}
