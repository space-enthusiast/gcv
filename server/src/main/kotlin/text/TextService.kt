package com.github.spaceenthusiast.text

import com.github.spaceenthusiast.AppConfig
import com.github.spaceenthusiast.encryption.EncryptionService
import com.github.spaceenthusiast.key.TextKeyGenerator
import com.github.spaceenthusiast.qr.QrGenerator
import com.github.spaceenthusiast.time.TimeProvider

class TextService(
    private val textRepository: TextRepository,
    private val textKeyGenerator: TextKeyGenerator,
    private val timeProvider: TimeProvider,
    private val qrGenerator: QrGenerator,
    private val encryptionService: EncryptionService,
    private val appConfig: AppConfig,
) {

    fun copy(request: CopyRequest): CopyResponse {
        val now = timeProvider.now()
        val id = textKeyGenerator.generate()

        val encryptedContent = encryptionService.encrypt(request.text)

        val entity = TextEntity(
            id = id,
            content = encryptedContent,
            ttl = request.ttl,
            expireAt = now.plusSeconds(request.ttl),
            /*
                # FEEDBACK
                this code is good practice but i would rather use require() in the first line of the function
             */
            pasteLimit = request.pasteLimit?.takeIf { it > 0 },
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

        if (text.pasteLimit != null && text.pasteCount >= text.pasteLimit)
            return PasteFailureResponse(message = "paste limit reached")

        textRepository.incrementPasteCount(id)

        val decryptedContent = encryptionService.decrypt(text.content)

        val link = appConfig.baseServerUrl + "/paste/" + text.id

        val qr = qrGenerator.generate(link)

        return PasteSuccessResponse(
            text = decryptedContent,
            qr = qr)
    }

    fun getQrImage(id: String): ByteArray {
        val link = appConfig.baseServerUrl + "/" + id
        return qrGenerator.generateImage(link)
    }
}