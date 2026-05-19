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
        require(request.pasteLimit == null || request.pasteLimit > 0) {
            "pasteLimit must be > 0 when provided"
        }

        val now = timeProvider.now()
        val id = textKeyGenerator.generate()

        val encryptedContent = encryptionService.encrypt(request.text)

        val entity = TextEntity(
            id = id,
            content = encryptedContent,
            ttl = request.ttl,
            expireAt = now.plusSeconds(request.ttl),
            remainingPastes = request.pasteLimit,
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

        if (text.remainingPastes != null) {
            val nextRemaining = text.remainingPastes - 1
            if (nextRemaining <= 0) {
                textRepository.delete(text.id)
            } else {
                textRepository.save(text.copy(remainingPastes = nextRemaining))
            }
        }

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