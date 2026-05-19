package com.github.spaceenthusiast.text

import java.time.LocalDateTime

data class TextEntity(
    val id: String,
    val content: ByteArray,
    val ttl: Long,
    val expireAt: LocalDateTime,
    val pasteLimit: Long? = null,
    val pasteCount: Long = 0,
)