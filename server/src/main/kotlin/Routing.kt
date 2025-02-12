package com.github.spaceenthusiast

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

@Serializable data class CopyRequest(val text: String, val ttl: Long)
@Serializable data class CopyResponse(val id: String)

@Serializable sealed class PasteResponse
@Serializable data class PasteSuccessResponse(val text: String) : PasteResponse()
@Serializable data class PasteFailureResponse(val message: String) : PasteResponse()

data class TextEntity(val id: String, val content: String, val ttl: Long, val expireAt: LocalDateTime)

interface TextRepository {
    fun save(entity: TextEntity)
    fun findBy(id: String): TextEntity?
}

class InMemoryTextRepository : TextRepository {
    private val map = ConcurrentHashMap<String, TextEntity>()

    override fun save(entity: TextEntity) {
        map[entity.id] = entity
    }

    override fun findBy(id: String): TextEntity? {
        return map[id]
    }
}

interface TimeProvider {
    fun now(): LocalDateTime
}

class LocalDateTimeProvider : TimeProvider {
    override fun now(): LocalDateTime {
        return LocalDateTime.now()
    }
}

fun Application.configureRouting(
    textService: TextService,
) {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        post("/copy") {
            val request = call.receive<CopyRequest>()
            val response = textService.copy(request = request)
            call.respond(response)
        }
        get("/paste/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val response = textService.paste(id)
            when (response) {
                is PasteSuccessResponse -> call.respond(response)
                is PasteFailureResponse -> call.respond(HttpStatusCode.NotFound, response)
            }
        }
    }
}
