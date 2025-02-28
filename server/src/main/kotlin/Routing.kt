package com.github.spaceenthusiast

import com.github.spaceenthusiast.presentation.WebApp
import com.github.spaceenthusiast.text.CopyRequest
import com.github.spaceenthusiast.text.PasteFailureResponse
import com.github.spaceenthusiast.text.PasteSuccessResponse
import com.github.spaceenthusiast.text.TextService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*

fun Application.configureRouting(
    textService: TextService,
    webApp: WebApp,
) {
    routing {
        get("/") {
            call.respondHtml {
                webApp.index(this)
            }
        }
        get("/form") {
            call.respondHtml {
                webApp.form(this)
            }
        }
        post("/submit") {
            val params = call.receiveParameters()
            val text = params.getOrFail("text")
            val response = textService.copy(request = CopyRequest(
                text = text,
                ttl = 60 * 10 // it's default value now. TODO change
            ))

            call.respondHtml {
                webApp.submit(this, response.id)
            }
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
        get("/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            call.respondHtml {
                webApp.id(this, id)
            }
        }
        get("/page/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            when (val response = textService.paste(id)) {
                is PasteSuccessResponse -> call.respondHtml {
                    webApp.pageId(this, response.text)
                }
                is PasteFailureResponse -> call.respondHtml {
                    webApp.pageIdNotFound(this)
                }
            }
        }
    }
}