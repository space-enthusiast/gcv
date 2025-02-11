package com.github.spaceenthusiast

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.*
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

interface TextKeyGenerator {
    fun generate(): String
}

class RandomKeyGenerator : TextKeyGenerator {
    override fun generate(): String {
        return UUID.randomUUID().toString().replace("-", "") // TODO Change ID generation algorithm
    }
}

class TextService(
    private val textRepository: TextRepository,
    private val textKeyGenerator: TextKeyGenerator,
    private val timeProvider: TimeProvider) {

    fun copy(request: CopyRequest): CopyResponse {
        val now = timeProvider.now()
        val id = textKeyGenerator.generate()
        val entity = TextEntity(
            id = id,
            content = request.text,
            ttl = request.ttl,
            expireAt = now.plusSeconds(request.ttl),
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

        return PasteSuccessResponse(text = text.content)
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
