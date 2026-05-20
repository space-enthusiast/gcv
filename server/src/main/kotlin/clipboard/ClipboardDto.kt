package com.github.spaceenthusiast.clipboard

import kotlinx.serialization.Serializable

@Serializable
data class CopyTextRequest(val text: String, val ttl: Long, val pasteLimit: Int? = null)

@Serializable
data class CopyTextResponse(val id: String)

@Serializable
data class CopyFilesRequest(
    val files: List<FileMetadata>,
    val ttl: Long,
    val pasteLimit: Int? = null,
)

@Serializable
data class FileMetadata(
    val filename: String,
    val sizeBytes: Long,
    val contentType: String,
)

@Serializable
data class CopyFilesResponse(
    val id: String,
    val uploads: List<PresignedUpload>,
)

@Serializable
data class PresignedUpload(
    val filename: String,
    val objectKey: String,
    val putUrl: String,
    val headers: Map<String, String>,
)

@Serializable
data class PresignedDownload(
    val filename: String,
    val sizeBytes: Long,
    val getUrl: String,
    val headers: Map<String, String>,
)

@Serializable
sealed class PasteResponse

@Serializable
data class PasteSuccessResponse(val text: String, val qr: String) : PasteResponse()

@Serializable
data class PasteFilesSuccess(val files: List<PresignedDownload>) : PasteResponse()

@Serializable
data class PasteFailureResponse(val message: String) : PasteResponse()
