package com.github.spaceenthusiast

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
import kotlinx.html.*


private fun HTML.template(block: DIV.() -> Unit) {
    head {
        title("GCV")
        script { src = "https://unpkg.com/htmx.org@2.0.4" }
        //link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css")
        link(rel = "stylesheet", href = "https://cdn.jsdelivr.net/npm/water.css@2/out/water.css")
    }
    body {
        h1 { +"GCV" }
        div {
            id = "response"
            block()
        }
    }
}

fun Application.configureRouting(
    textService: TextService,
    appConfig: AppConfig,
) {
    routing {
        get("/") {
            call.respondHtml {
                template {
                    attributes["hx-get"] = "/form"
                    attributes["hx-trigger"] = "load"
                    attributes["hx-target"] = "this"
                }
            }
        }
        get("/form") {
            call.respondHtml {
                body {
                    h2 { +"copy your text" }
                    form {
                        attributes["hx-post"] = "/submit"
                        attributes["hx-target"] = "#response"
                        attributes["hx-swap"] = "innerHTML"

                        textArea {
                            name = "text"
                            rows = "5"
                            cols = "50"
                        }
                        br()
                        button {
                            type = ButtonType.submit
                            +"Submit"
                        }
                    }
                }
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
                body {
                    div {
                        id = "response"
                        p { +"Successfully uploaded" }
                        p { +"id: ${response.id}"}
                        a(href = "${appConfig.baseServerUrl}/${response.id}") {
                            +"${appConfig.baseServerUrl}/${response.id}"
                        }
                        br()
                        br()
                        button {
                            attributes["hx-get"] = "/form"
                            attributes["hx-target"] = "#response"
                            +"copy more text"
                        }
                    }
                }
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
                template {
                    attributes["hx-get"] = "/page/${id}"
                    attributes["hx-trigger"] = "load"
                    attributes["hx-target"] = "this"
                }
            }
        }
        get("/page/{id}") {
            val id = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val response = textService.paste(id)
            when (response) {
                is PasteSuccessResponse -> call.respondHtml {
                    body {
                        textArea {
                            name = "text"
                            rows = "5"
                            cols = "50"

                            +response.text
                        }
                        br()
                        button {
                            attributes["hx-get"] = "/form"
                            attributes["hx-target"] = "#response"
                            +"copy more text"
                        }
                    }
                }
                is PasteFailureResponse -> call.respondHtml {
                    body {
                        h2 { +"no text founded" }
                        p { +"text can be outdated" }
                        button {
                            attributes["hx-get"] = "/form"
                            attributes["hx-target"] = "#response"
                            +"copy more text"
                        }
                    }
                }
            }
        }
    }
}