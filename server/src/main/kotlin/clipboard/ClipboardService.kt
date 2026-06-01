package com.github.spaceenthusiast.clipboard

import com.github.spaceenthusiast.AppConfig
import com.github.spaceenthusiast.encryption.EncryptionService
import com.github.spaceenthusiast.key.TextKeyGenerator
import com.github.spaceenthusiast.qr.QrGenerator
import com.github.spaceenthusiast.storage.ObjectStorage
import com.github.spaceenthusiast.time.TimeProvider
import java.security.SecureRandom

class ClipboardService(
    private val clipboardRepository: ClipboardRepository,
    private val textKeyGenerator: TextKeyGenerator,
    private val timeProvider: TimeProvider,
    private val qrGenerator: QrGenerator,
    private val encryptionService: EncryptionService,
    private val objectStorage: ObjectStorage,
    private val appConfig: AppConfig,
    private val secureRandom: SecureRandom = SecureRandom(),
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

    suspend fun copyFiles(request: CopyFilesRequest): CopyFilesResponse {
        require(request.files.isNotEmpty()) { "at least one file required" }
        require(request.files.size <= appConfig.maxFilesPerBundle) {
            "too many files in bundle (max ${appConfig.maxFilesPerBundle})"
        }
        request.files.forEach { f ->
            require(f.filename.length in 1..255) { "filename length must be in 1..255" }
            require(!f.filename.contains('/') && !f.filename.contains("..")) {
                "filename must not contain '/' or '..'"
            }
            require(f.sizeBytes in 1..appConfig.perFileMaxBytes) {
                "file size must be in 1..${appConfig.perFileMaxBytes} bytes"
            }
        }
        require(request.files.sumOf { it.sizeBytes } <= appConfig.bundleMaxBytes) {
            "bundle size exceeds ${appConfig.bundleMaxBytes} bytes"
        }
        require(request.ttl in 1..appConfig.fileTtlMaxSeconds) {
            "ttl must be in 1..${appConfig.fileTtlMaxSeconds} seconds"
        }
        require(request.pasteLimit == null || request.pasteLimit > 0) {
            "pasteLimit must be > 0 when provided"
        }

        val now = timeProvider.now()
        val id = textKeyGenerator.generate()
        val sseKey = ByteArray(32).also { secureRandom.nextBytes(it) }

        val fileRefs = request.files.mapIndexed { index, f ->
            FileRef(
                objectKey = "$id/$index-${f.filename}",
                filename = f.filename,
                sizeBytes = f.sizeBytes,
                contentType = f.contentType,
            )
        }

        val entity = ClipboardEntry(
            id = id,
            payload = Payload.Files(files = fileRefs, sseKey = sseKey),
            ttl = request.ttl,
            expireAt = now.plusSeconds(request.ttl),
            remainingPastes = request.pasteLimit,
        )

        clipboardRepository.save(entity)

        val uploads = fileRefs.map { ref ->
            val presigned = objectStorage.presignPut(
                key = ref.objectKey,
                contentType = ref.contentType,
                sseKey = sseKey,
                ttl = appConfig.presignTtl,
            )
            PresignedUpload(
                filename = ref.filename,
                objectKey = ref.objectKey,
                putUrl = presigned.url,
                headers = presigned.headers,
            )
        }

        return CopyFilesResponse(id = id, uploads = uploads)
    }

    suspend fun paste(id: String): PasteResponse {
        val now = timeProvider.now()
        val entry = clipboardRepository.findBy(id)
            ?: return PasteFailureResponse(message = "id not found")

        if (entry.expireAt < now) {
            deleteEntry(entry)
            return PasteFailureResponse(message = "ttl has expired")
        }

        val terminal = isTerminalPaste(entry)

        return when (val payload = entry.payload) {
            is Payload.Text -> {
                consumePaste(entry, terminal)
                val decryptedContent = encryptionService.decrypt(payload.cipher)
                val link = appConfig.baseServerUrl + "/paste/" + entry.id
                val qr = qrGenerator.generate(link)
                PasteSuccessResponse(text = decryptedContent, qr = qr)
            }
            is Payload.Files -> {
                val downloads = payload.files.map { ref ->
                    val presigned = objectStorage.presignGet(
                        key = ref.objectKey,
                        sseKey = payload.sseKey,
                        ttl = appConfig.presignTtl,
                    )
                    PresignedDownload(
                        filename = ref.filename,
                        sizeBytes = ref.sizeBytes,
                        getUrl = presigned.url,
                        headers = presigned.headers,
                    )
                }
                consumePaste(entry, terminal)
                PasteFilesSuccess(files = downloads)
            }
        }
    }

    fun getQrImage(id: String): ByteArray {
        val link = appConfig.baseServerUrl + "/" + id
        return qrGenerator.generateImage(link)
    }

    suspend fun deleteExpired(): Int {
        val now = timeProvider.now()
        val expired = clipboardRepository.findAll().filter { it.expireAt < now }
        expired.forEach { deleteEntry(it) }
        return expired.size
    }

    private fun isTerminalPaste(entry: ClipboardEntry): Boolean {
        val remaining = entry.remainingPastes ?: return false
        return remaining - 1 <= 0
    }

    private suspend fun consumePaste(entry: ClipboardEntry, terminal: Boolean) {
        if (entry.remainingPastes == null) return
        if (terminal) {
            deleteEntry(entry)
        } else {
            clipboardRepository.save(entry.copy(remainingPastes = entry.remainingPastes - 1))
        }
    }

    private suspend fun deleteEntry(entry: ClipboardEntry) {
        clipboardRepository.delete(entry.id)
        val payload = entry.payload
        if (payload is Payload.Files) {
            objectStorage.delete(payload.files.map { it.objectKey })
        }
    }
}
