package com.github.spaceenthusiast

import com.github.spaceenthusiast.clipboard.ClipboardService
import com.github.spaceenthusiast.clipboard.CopyFilesRequest
import com.github.spaceenthusiast.clipboard.CopyTextRequest
import com.github.spaceenthusiast.clipboard.PasteFailureResponse
import com.github.spaceenthusiast.clipboard.PasteFilesSuccess
import com.github.spaceenthusiast.clipboard.PasteSuccessResponse
import com.github.spaceenthusiast.presentation.WebApp
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*

fun Application.configureRouting(
    clipboardService: ClipboardService,
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
            val pasteLimit = params["pasteLimit"]?.takeIf { it.isNotBlank() }?.toInt()
            val response = clipboardService.copyText(request = CopyTextRequest(
                text = text,
                ttl = 60 * 10,
                pasteLimit = pasteLimit,
            ))

            call.respondHtml {
                webApp.submit(this, response.id)
            }
        }
        post("/copy") {
            val request = call.receive<CopyTextRequest>()
            val response = clipboardService.copyText(request = request)
            call.respond(response)
        }
        post("/copy/files") {
            val request = call.receive<CopyFilesRequest>()
            val response = clipboardService.copyFiles(request = request)
            call.respond(response)
        }
        get("/submitted/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            call.respondHtml {
                webApp.submit(this, id)
            }
        }
        get("/paste/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            when (val response = clipboardService.paste(id)) {
                is PasteSuccessResponse -> call.respond(response)
                is PasteFilesSuccess -> call.respond(response)
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

            when (val response = clipboardService.paste(id)) {
                is PasteSuccessResponse -> call.respondHtml {
                    webApp.pageId(this, response.text, id)
                }
                is PasteFilesSuccess -> call.respondHtml {
                    webApp.pageIdFiles(this, response.files, id)
                }
                is PasteFailureResponse -> call.respondHtml {
                    webApp.pageIdNotFound(this)
                }
            }
        }
        get("/qr/{id}.png") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val response = clipboardService.getQrImage(id)
            call.respondBytes(response, ContentType.Image.PNG)
        }
    }
}
