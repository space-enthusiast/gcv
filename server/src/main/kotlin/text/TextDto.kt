package com.github.spaceenthusiast.text

import kotlinx.serialization.Serializable

@Serializable
data class CopyRequest(val text: String, val ttl: Long, val maxPasteCount: Int = 10)
@Serializable
data class CopyResponse(val id: String)

@Serializable
sealed class PasteResponse
@Serializable
data class PasteSuccessResponse(val text: String, val qr: String) : PasteResponse()
@Serializable
data class PasteFailureResponse(val message: String) : PasteResponse()
