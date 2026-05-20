package com.github.spaceenthusiast.clipboard

sealed interface Payload {
    data class Text(val cipher: ByteArray) : Payload
}
