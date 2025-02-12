package com.github.spaceenthusiast

import com.github.spaceenthusiast.key.TinyKeyGenerator
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureRouting(TextService(
            textKeyGenerator = TinyKeyGenerator(),
            textRepository = InMemoryTextRepository(),
            timeProvider = LocalDateTimeProvider(),
        ))
}
