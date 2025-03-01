package com.github.spaceenthusiast

import com.github.spaceenthusiast.encryption.EncryptionService
import com.github.spaceenthusiast.key.TinyKeyGenerator
import com.github.spaceenthusiast.presentation.WebApp
import com.github.spaceenthusiast.qr.QrGenerator
import com.github.spaceenthusiast.s3.S3Service
import com.github.spaceenthusiast.text.InMemoryTextRepository
import com.github.spaceenthusiast.text.S3TextRepository
import com.github.spaceenthusiast.text.TextRepository
import com.github.spaceenthusiast.text.TextService
import com.github.spaceenthusiast.time.LocalDateTimeProvider
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val appConfig = AppConfig()

    val textRepository: TextRepository = if (appConfig.useS3) {
        S3TextRepository(s3Service = S3Service(appConfig.s3Config))
    } else {
        InMemoryTextRepository()
    }

    configureSerialization()
    configureRouting(
        textService = TextService(
            textKeyGenerator = TinyKeyGenerator(),
            textRepository = textRepository,
            timeProvider = LocalDateTimeProvider(),
            qrGenerator = QrGenerator(),
            appConfig = appConfig,
            encryptionService = EncryptionService(appConfig)
        ),
        webApp = WebApp(appConfig)
    )
}
