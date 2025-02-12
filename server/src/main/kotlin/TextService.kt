package com.github.spaceenthusiast

import com.github.spaceenthusiast.key.TextKeyGenerator

class TextService(
    private val textRepository: TextRepository,
    private val textKeyGenerator: TextKeyGenerator,
    private val timeProvider: TimeProvider
) {

    fun copy(request: CopyRequest): CopyResponse {
        val now = timeProvider.now()
        val id = textKeyGenerator.generate()
        val entity = TextEntity(
            id = id,
            content = request.text,
            ttl = request.ttl,
            expireAt = now.plusSeconds(request.ttl),
        )

        textRepository.save(entity)

        return CopyResponse(id)
    }

    fun paste(id: String): PasteResponse {
        val now = timeProvider.now()
        val text = textRepository.findBy(id)
            ?: return PasteFailureResponse(message = "id not found")

        if (text.expireAt < now)
            return PasteFailureResponse(message = "ttl has expired")

        return PasteSuccessResponse(text = text.content)
    }
}