package com.github.spaceenthusiast.clipboard

import com.github.spaceenthusiast.AppConfig
import com.github.spaceenthusiast.encryption.EncryptionService
import com.github.spaceenthusiast.key.TextKeyGenerator
import com.github.spaceenthusiast.qr.QrGenerator
import com.github.spaceenthusiast.time.TimeProvider

class ClipboardService(
    private val clipboardRepository: ClipboardRepository,
    private val textKeyGenerator: TextKeyGenerator,
    private val timeProvider: TimeProvider,
    private val qrGenerator: QrGenerator,
    private val encryptionService: EncryptionService,
    private val appConfig: AppConfig,
) {

    fun copyText(request: CopyTextRequest): CopyTextResponse {
        require(request.pasteLimit == null || request.pasteLimit > 0) {
            "pasteLimit must be > 0 when provided"
        }

        val now = timeProvider.now()
        val id = textKeyGenerator.generate()

        val encryptedContent = encryptionService.encrypt(request.text)

        val entity = ClipboardEntry(
            id = id,
            payload = Payload.Text(cipher = encryptedContent),
            ttl = request.ttl,
            expireAt = now.plusSeconds(request.ttl),
            remainingPastes = request.pasteLimit,
        )

        clipboardRepository.save(entity)

        return CopyTextResponse(id)
    }

    fun paste(id: String): PasteResponse {
        val now = timeProvider.now()
        val entry = clipboardRepository.findBy(id)
            ?: return PasteFailureResponse(message = "id not found")

        if (entry.expireAt < now)
            return PasteFailureResponse(message = "ttl has expired")

        consumePaste(entry)

        return when (val payload = entry.payload) {
            is Payload.Text -> {
                val decryptedContent = encryptionService.decrypt(payload.cipher)
                val link = appConfig.baseServerUrl + "/paste/" + entry.id
                val qr = qrGenerator.generate(link)
                PasteSuccessResponse(text = decryptedContent, qr = qr)
            }
        }
    }

    fun getQrImage(id: String): ByteArray {
        val link = appConfig.baseServerUrl + "/" + id
        return qrGenerator.generateImage(link)
    }

    private fun consumePaste(entry: ClipboardEntry) {
        if (entry.remainingPastes == null) return
        val next = entry.remainingPastes - 1
        if (next <= 0) {
            clipboardRepository.delete(entry.id)
        } else {
            clipboardRepository.save(entry.copy(remainingPastes = next))
        }
    }
}
