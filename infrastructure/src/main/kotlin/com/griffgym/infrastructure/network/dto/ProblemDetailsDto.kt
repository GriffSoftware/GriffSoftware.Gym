package com.griffgym.infrastructure.network.dto

import kotlinx.serialization.Serializable

/**
 * The RFC 9457 document every Griff Gym failure arrives as.
 *
 * Every field is optional, including [status]. Not every 4xx comes from the server's own
 * exception handler — an unauthenticated request is refused by the JWT middleware before a
 * controller is ever reached, and a proxy or a captive portal can answer with HTML — so a
 * mapper that assumed a body, or assumed it was JSON, would turn a routine 401 into a crash.
 *
 * [expectedVersion] and [actualVersion] are the `ProblemDetails.Extensions` written for an
 * optimistic-concurrency conflict. They are the difference between "somebody else got there
 * first, re-read and merge" and "this can never work", so they are read here rather than
 * being dropped with the other unknown keys.
 */
@Serializable
internal data class ProblemDetailsDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val errors: Map<String, List<String>>? = null,
    val expectedVersion: Int? = null,
    val actualVersion: Int? = null,
) {
    /**
     * What to put in front of the lifter. `detail` is the sentence written for a human;
     * `title` is the category, and only worth showing when there is nothing better.
     */
    fun messageOr(fallback: String): String =
        detail?.takeIf(String::isNotBlank)
            ?: title?.takeIf(String::isNotBlank)
            ?: fallback
}
