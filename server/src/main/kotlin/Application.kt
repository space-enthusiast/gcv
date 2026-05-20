package com.github.spaceenthusiast

import com.github.spaceenthusiast.clipboard.ClipboardJanitor
import com.github.spaceenthusiast.clipboard.ClipboardService
import com.github.spaceenthusiast.clipboard.InMemoryClipboardRepository
import com.github.spaceenthusiast.encryption.EncryptionService
import com.github.spaceenthusiast.key.TinyKeyGenerator
import com.github.spaceenthusiast.presentation.WebApp
import com.github.spaceenthusiast.qr.QrGenerator
import com.github.spaceenthusiast.storage.SeaweedFsObjectStorage
import com.github.spaceenthusiast.time.LocalDateTimeProvider
import io.ktor.server.application.*
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val appConfig = AppConfig()

    val objectStorage = SeaweedFsObjectStorage(
        endpoint = appConfig.seaweedEndpoint,
        bucket = appConfig.seaweedBucket,
        accessKey = appConfig.seaweedAccessKey,
        secretKey = appConfig.seaweedSecretKey,
    )

    val clipboardService = ClipboardService(
        textKeyGenerator = TinyKeyGenerator(),
        clipboardRepository = InMemoryClipboardRepository(),
        timeProvider = LocalDateTimeProvider(),
        qrGenerator = QrGenerator(),
        appConfig = appConfig,
        encryptionService = EncryptionService(appConfig),
        objectStorage = objectStorage,
    )

    val janitor = ClipboardJanitor(clipboardService, appConfig.janitorSweepSeconds.seconds)
    janitor.start()

    monitor.subscribe(ApplicationStopped) {
        janitor.stop()
        objectStorage.close()
    }

    configureSerialization()
    configureRouting(
        clipboardService = clipboardService,
        webApp = WebApp(appConfig),
    )
}
