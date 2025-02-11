package com.github.spaceenthusiast

import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureRouting(TextService(
            textKeyGenerator = RandomKeyGenerator(),
            textRepository = InMemoryTextRepository(),
            timeProvider = LocalDateTimeProvider(),
        ))
}
