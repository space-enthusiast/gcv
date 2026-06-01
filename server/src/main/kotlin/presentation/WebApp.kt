package com.github.spaceenthusiast.presentation

import com.github.spaceenthusiast.AppConfig
import com.github.spaceenthusiast.clipboard.PresignedDownload
import kotlinx.html.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class WebApp(
    private val appConfig: AppConfig
) {

    private fun HTML.template(block: DIV.() -> Unit) {
        head {
            title("GCV")
            meta {
                name = "viewport"
                content = "width=device-width, initial-scale=1"
            }
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
                label {
                    htmlFor = "pasteLimit"
                    +"paste limit (leave blank for unlimited): "
                }
                numberInput {
                    id = "pasteLimit"
                    name = "pasteLimit"
                    min = "1"
                }
                br()
                button {
                    type = ButtonType.submit
                    +"Submit"
                }
            }

            h2 { +"or copy files" }
            form {
                id = "gcvFilesForm"
                attributes["onsubmit"] = "event.preventDefault(); gcvUpload(this);"

                fileInput {
                    name = "files"
                    multiple = true
                    required = true
                }
                br()
                label {
                    htmlFor = "filesPasteLimit"
                    +"paste limit (leave blank for unlimited): "
                }
                numberInput {
                    id = "filesPasteLimit"
                    name = "pasteLimit"
                    min = "1"
                }
                br()
                button {
                    type = ButtonType.submit
                    +"Upload files"
                }
            }
            script { unsafe { +GCV_UPLOAD_JS } }
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
                img {
                    src = "/qr/$textId.png"
                }
                br()
                br()
                copyMoreTextButton()
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

    fun pageId(html: HTML, text: String, textId: String) {
        html.body {
            textArea {
                name = "text"
                rows = "5"
                cols = "50"

                +text
            }
            br()
            img {
                src = "/qr/$textId.png"
            }
            br()
            br()
            copyMoreTextButton()
        }
    }

    fun pageIdFiles(html: HTML, files: List<PresignedDownload>, textId: String) {
        html.body {
            h2 { +"files paste #${textId}" }
            p { +"click each file to download (SSE-C headers are replayed by the browser)" }
            ul {
                files.forEach { f ->
                    li {
                        button(type = ButtonType.button) {
                            attributes["data-url"] = f.getUrl
                            attributes["data-filename"] = f.filename
                            attributes["data-headers"] = headersToJson(f.headers)
                            attributes["onclick"] = "gcvDownload(this)"
                            +"${f.filename} (${f.sizeBytes} bytes)"
                        }
                    }
                }
            }
            br()
            img {
                src = "/qr/$textId.png"
            }
            br()
            br()
            copyMoreTextButton()
            script { unsafe { +GCV_DOWNLOAD_JS } }
        }
    }

    fun pageIdNotFound(html: HTML) {
        html.body {
            h2 { +"no text founded" }
            p { +"text can be outdated" }
            copyMoreTextButton()
        }
    }

    private fun FlowContent.copyMoreTextButton() {
        button {
            attributes["hx-get"] = "/form"
            attributes["hx-target"] = "#response"
            attributes["hx-push-url"] = "/"
            +"copy more text"
        }
    }

    private fun headersToJson(headers: Map<String, String>): String {
        val obj = JsonObject(headers.mapValues { (_, v) -> JsonPrimitive(v) })
        return Json.encodeToString(JsonObject.serializer(), obj)
    }

    companion object {
        private val GCV_UPLOAD_JS = """
            async function gcvUpload(form) {
              const files = Array.from(form.files.files);
              if (files.length === 0) return;
              const meta = files.map(f => ({
                filename: f.name,
                sizeBytes: f.size,
                contentType: f.type || 'application/octet-stream'
              }));
              const pasteLimitInput = form.pasteLimit.value;
              const pasteLimit = pasteLimitInput ? parseInt(pasteLimitInput, 10) : null;
              const r = await fetch('/copy/files', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ files: meta, ttl: 600, pasteLimit: pasteLimit })
              });
              if (!r.ok) {
                alert('copy failed: ' + r.status + ' ' + (await r.text()));
                return;
              }
              const reg = await r.json();
              await Promise.all(reg.uploads.map((u, i) => fetch(u.putUrl, {
                method: 'PUT', headers: u.headers, body: files[i]
              }).then(resp => {
                if (!resp.ok) throw new Error('PUT failed for ' + u.filename + ': ' + resp.status);
              })));
              htmx.ajax('GET', '/submitted/' + reg.id, { target: '#response', swap: 'innerHTML' });
            }
        """.trimIndent()

        private val GCV_DOWNLOAD_JS = """
            async function gcvDownload(btn) {
              const url = btn.dataset.url;
              const filename = btn.dataset.filename;
              const headers = JSON.parse(btn.dataset.headers);
              const r = await fetch(url, { method: 'GET', headers: headers });
              if (!r.ok) {
                alert('download failed: ' + r.status);
                return;
              }
              const blob = await r.blob();
              const objectUrl = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = objectUrl;
              a.download = filename;
              document.body.appendChild(a);
              a.click();
              document.body.removeChild(a);
              URL.revokeObjectURL(objectUrl);
            }
        """.trimIndent()
    }
}
