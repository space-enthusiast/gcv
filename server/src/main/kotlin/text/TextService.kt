package com.github.spaceenthusiast.text

import com.github.spaceenthusiast.AppConfig
import com.github.spaceenthusiast.key.TextKeyGenerator
import com.github.spaceenthusiast.qr.QrGenerator
import com.github.spaceenthusiast.time.TimeProvider

class TextService(
    private val textRepository: TextRepository,
    private val textKeyGenerator: TextKeyGenerator,
    private val timeProvider: TimeProvider,
    private val qrGenerator: QrGenerator,
    private val appConfig: AppConfig,
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

        val link = appConfig.baseServerUrl + "/paste/" + text.id

        val qr = qrGenerator.generate(link)

        return PasteSuccessResponse(
            text = text.content,
            qr = qr)
    }
}