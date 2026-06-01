package com.github.spaceenthusiast

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AppConfig {

    val baseServerUrl: String = System.getenv("BASE_SERVER_URL") ?: "http://127.0.0.1:8080"
    val encryptionKey: String = System.getenv("ENCRYPTION_KEY") ?: "oyoushouldchange"

    val seaweedEndpoint: String = System.getenv("SEAWEED_ENDPOINT") ?: "http://127.0.0.1:8333"
    val seaweedBucket: String = System.getenv("SEAWEED_BUCKET") ?: "gcv"
    val seaweedAccessKey: String = System.getenv("SEAWEED_ACCESS_KEY") ?: "any"
    val seaweedSecretKey: String = System.getenv("SEAWEED_SECRET_KEY") ?: "any"

    val presignTtl: Duration = (System.getenv("PRESIGN_TTL_SECONDS")?.toLongOrNull()?.seconds) ?: 5.minutes
    val fileTtlMaxSeconds: Long = System.getenv("FILE_TTL_MAX_SECONDS")?.toLongOrNull() ?: 600L
    val perFileMaxBytes: Long = System.getenv("PER_FILE_MAX_BYTES")?.toLongOrNull() ?: (50L * 1024 * 1024)
    val bundleMaxBytes: Long = System.getenv("BUNDLE_MAX_BYTES")?.toLongOrNull() ?: (200L * 1024 * 1024)
    val maxFilesPerBundle: Int = System.getenv("MAX_FILES_PER_BUNDLE")?.toIntOrNull() ?: 20
    val janitorSweepSeconds: Long = System.getenv("JANITOR_SWEEP_SECONDS")?.toLongOrNull() ?: 30L
}
