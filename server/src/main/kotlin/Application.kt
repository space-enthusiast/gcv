package com.github.spaceenthusiast

import com.github.spaceenthusiast.key.TinyKeyGenerator
import com.github.spaceenthusiast.qr.QrGenerator
import com.github.spaceenthusiast.text.InMemoryTextRepository
import com.github.spaceenthusiast.text.TextService
import com.github.spaceenthusiast.time.LocalDateTimeProvider
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureRouting(
        TextService(
            textKeyGenerator = TinyKeyGenerator(),
            textRepository = InMemoryTextRepository(),
            timeProvider = LocalDateTimeProvider(),
            qrGenerator = QrGenerator(),
            appConfig = AppConfig()
        )
    )
}
