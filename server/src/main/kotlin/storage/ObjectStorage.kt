package com.github.spaceenthusiast.storage

import kotlin.time.Duration

data class PresignedRequest(
    val url: String,
    val headers: Map<String, String>,
)

interface ObjectStorage {
    suspend fun presignPut(
        key: String,
        contentType: String,
        sseKey: ByteArray,
        ttl: Duration,
    ): PresignedRequest

    suspend fun presignGet(
        key: String,
        sseKey: ByteArray,
        ttl: Duration,
    ): PresignedRequest

    suspend fun delete(keys: List<String>)
}
