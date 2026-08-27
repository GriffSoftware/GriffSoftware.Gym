package com.griffgym.infrastructure.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Reads a .NET `DateTimeOffset` into an [Instant].
 *
 * The server writes offsets, not always `Z` — `2026-03-02T18:00:00+00:00` is as valid a
 * rendering of the same moment as `2026-03-02T18:00:00Z`, and which one arrives depends on
 * how the value was constructed server-side. [Instant.parse] only accepts the latter, so
 * decoding goes through [OffsetDateTime]; a client that assumed `Z` would fail on a
 * perfectly conformant response.
 *
 * Encoding always emits `Z`, which .NET parses without complaint.
 */
internal object InstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    }

    override fun deserialize(decoder: Decoder): Instant =
        OffsetDateTime.parse(decoder.decodeString()).toInstant()
}

/**
 * A .NET `DateOnly`, which is the calendar day a session was trained on.
 *
 * Deliberately not an [Instant]: "the workout of 4 November" has no time zone, and turning it
 * into one would move a late-evening session into the next day for a lifter who travels.
 */
internal object LocalDateSerializer : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(DateTimeFormatter.ISO_LOCAL_DATE.format(value))
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}
