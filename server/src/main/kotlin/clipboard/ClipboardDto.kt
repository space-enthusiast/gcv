package com.github.spaceenthusiast.clipboard

import kotlinx.serialization.Serializable

@Serializable
data class CopyTextRequest(val text: String, val ttl: Long, val pasteLimit: Int? = null)

@Serializable
data class CopyTextResponse(val id: String)

@Serializable
sealed class PasteResponse

@Serializable
data class PasteSuccessResponse(val text: String, val qr: String) : PasteResponse()

@Serializable
data class PasteFailureResponse(val message: String) : PasteResponse()
