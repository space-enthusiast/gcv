package com.github.spaceenthusiast.clipboard

import java.time.LocalDateTime

data class ClipboardEntry(
    val id: String,
    val payload: Payload,
    val ttl: Long,
    val expireAt: LocalDateTime,
    val remainingPastes: Int?,
)
