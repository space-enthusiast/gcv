package com.github.spaceenthusiast.presentation

import com.github.spaceenthusiast.AppConfig
import kotlinx.html.*

class WebApp(
    private val appConfig: AppConfig
) {

    private fun HTML.template(block: DIV.() -> Unit) {
        head {
            title("GCV")
            script { src = "https://unpkg.com/htmx.org@2.0.4" }
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

    fun index(html: HTML) {
        html.template {
            attributes["hx-get"] = "/form"
            attributes["hx-trigger"] = "load"
            attributes["hx-target"] = "this"
        }
    }

    fun form(html: HTML) {
        html.body {
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

    fun submit(html: HTML, textId: String) {
        html.body {
            div {
                id = "response"
                p { +"Successfully uploaded" }
                p { +"id: $textId"}
                a(href = "${appConfig.baseServerUrl}/${textId}") {
                    +"${appConfig.baseServerUrl}/${textId}"
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

    fun id(html: HTML, textId: String) {
        html.template {
            attributes["hx-get"] = "/page/${textId}"
            attributes["hx-trigger"] = "load"
            attributes["hx-target"] = "this"
        }
    }

    fun pageId(html: HTML, text: String) {
        html.body {
            textArea {
                name = "text"
                rows = "5"
                cols = "50"

                +text
            }
            br()
            button {
                attributes["hx-get"] = "/form"
                attributes["hx-target"] = "#response"
                +"copy more text"
            }
        }
    }

    fun pageIdNotFound(html: HTML) {
        html.body {
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