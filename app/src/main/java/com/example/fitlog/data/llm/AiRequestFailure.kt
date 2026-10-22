package com.example.fitlog.data.llm

/**
 * What went wrong during one request against an OpenAI-compatible provider.
 *
 * This is the vocabulary of the protocol layer, shared by every feature that calls a provider
 * (diary extraction today, agent answers next). Feature-specific shape errors are deliberately
 * absent: a diary JSON schema violation belongs to the diary feature, not to the protocol.
 *
 * [isFatal] marks the failures that make every following request in the same run pointless (no
 * network, bad credential, rate limit). One unparsable answer is not fatal.
 */
internal enum class AiRequestFailure(val isFatal: Boolean) {
    TIMEOUT(true),
    NETWORK_ERROR(true),
    AUTHENTICATION_ERROR(true),
    PERMISSION_DENIED(true),
    RATE_LIMITED(true),
    REQUEST_REJECTED(true),
    SERVICE_UNAVAILABLE(true),
    EMPTY_RESPONSE(false),
    INVALID_RESPONSE(false),
    TRUNCATED_RESPONSE(false),
    MODEL_REFUSAL(false),
}
