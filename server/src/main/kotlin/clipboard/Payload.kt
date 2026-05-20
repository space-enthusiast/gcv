package com.github.spaceenthusiast.clipboard

sealed interface Payload {
    data class Text(val cipher: ByteArray) : Payload

    data class Files(
        val files: List<FileRef>,
        val sseKey: ByteArray,
    ) : Payload
}

data class FileRef(
    val objectKey: String,
    val filename: String,
    val sizeBytes: Long,
    val contentType: String,
)
