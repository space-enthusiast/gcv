package com.github.spaceenthusiast

import com.github.spaceenthusiast.text.CopyRequest
import com.github.spaceenthusiast.text.PasteFailureResponse
import com.github.spaceenthusiast.text.PasteSuccessResponse
import com.github.spaceenthusiast.text.TextService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


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
