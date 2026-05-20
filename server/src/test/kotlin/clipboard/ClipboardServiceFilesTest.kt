package clipboard

import com.github.spaceenthusiast.clipboard.CopyFilesRequest
import com.github.spaceenthusiast.clipboard.FileMetadata
import com.github.spaceenthusiast.clipboard.PasteFailureResponse
import com.github.spaceenthusiast.clipboard.PasteFilesSuccess
import com.github.spaceenthusiast.clipboard.Payload
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

class ClipboardServiceFilesTest : FunSpec({

    val sseHeaderKeys = setOf(
        "x-amz-server-side-encryption-customer-algorithm",
        "x-amz-server-side-encryption-customer-key",
        "x-amz-server-side-encryption-customer-key-MD5",
    )

    test("copy returns one presigned upload per file with SSE-C headers populated") {
        val fx = Fixtures()
        val response = runBlocking {
            fx.service.copyFiles(
                CopyFilesRequest(
                    files = listOf(
                        FileMetadata("a.txt", 10, "text/plain"),
                        FileMetadata("b.png", 20, "image/png"),
                    ),
                    ttl = 60,
                    pasteLimit = 1,
                )
            )
        }

        response.uploads.size shouldBe 2
        response.uploads[0].filename shouldBe "a.txt"
        response.uploads[1].filename shouldBe "b.png"
        response.uploads.forEach { it.headers.keys shouldContainAll sseHeaderKeys }
        response.uploads.forEach { it.headers["x-amz-server-side-encryption-customer-algorithm"] shouldBe "AES256" }
        fx.storage.puts.size shouldBe 2
        fx.storage.puts[0].key shouldBe "${response.id}/0-a.txt"
        fx.storage.puts[1].key shouldBe "${response.id}/1-b.png"
        fx.storage.puts[0].contentType shouldBe "text/plain"
        fx.storage.puts[1].contentType shouldBe "image/png"
    }

    test("paste returns one presigned download per file with the same SSE-C key") {
        val fx = Fixtures()
        val copy = runBlocking {
            fx.service.copyFiles(
                CopyFilesRequest(
                    files = listOf(FileMetadata("a.txt", 10, "text/plain")),
                    ttl = 60,
                    pasteLimit = null,
                )
            )
        }
        val storedKey = (fx.repo.findBy(copy.id)!!.payload as Payload.Files).sseKey

        val pasted = runBlocking { fx.service.paste(copy.id) }

        pasted.shouldBeInstanceOf<PasteFilesSuccess>()
        pasted.files.size shouldBe 1
        pasted.files[0].filename shouldBe "a.txt"
        pasted.files[0].sizeBytes shouldBe 10
        pasted.files[0].headers.keys shouldContainAll sseHeaderKeys
        fx.storage.gets.size shouldBe 1
        fx.storage.gets[0].key shouldBe "${copy.id}/0-a.txt"
        fx.storage.gets[0].sseKey.contentEquals(storedKey) shouldBe true
    }

    test("pasteLimit=1 deletes all object keys from storage on terminal paste") {
        val fx = Fixtures()
        val copy = runBlocking {
            fx.service.copyFiles(
                CopyFilesRequest(
                    files = listOf(
                        FileMetadata("a.txt", 1, "text/plain"),
                        FileMetadata("b.txt", 1, "text/plain"),
                    ),
                    ttl = 60,
                    pasteLimit = 1,
                )
            )
        }

        runBlocking { fx.service.paste(copy.id) }.shouldBeInstanceOf<PasteFilesSuccess>()

        fx.repo.findBy(copy.id) shouldBe null
        fx.storage.deletes.size shouldBe 1
        fx.storage.deletes[0] shouldContainExactly listOf("${copy.id}/0-a.txt", "${copy.id}/1-b.txt")
    }

    test("expired bundle returns failure and deletes object keys from storage") {
        val fx = Fixtures()
        val copy = runBlocking {
            fx.service.copyFiles(
                CopyFilesRequest(
                    files = listOf(FileMetadata("a.txt", 1, "text/plain")),
                    ttl = 5,
                    pasteLimit = null,
                )
            )
        }

        fx.time.advance(6)
        val pasted = runBlocking { fx.service.paste(copy.id) }

        pasted.shouldBeInstanceOf<PasteFailureResponse>()
        fx.repo.findBy(copy.id) shouldBe null
        fx.storage.deletes[0] shouldContainExactly listOf("${copy.id}/0-a.txt")
    }

    test("deleteExpired sweeps multiple expired bundles and reports the count") {
        val fx = Fixtures()
        val a = runBlocking {
            fx.service.copyFiles(
                CopyFilesRequest(
                    files = listOf(FileMetadata("a.txt", 1, "text/plain")),
                    ttl = 5, pasteLimit = null,
                )
            )
        }
        val b = runBlocking {
            fx.service.copyFiles(
                CopyFilesRequest(
                    files = listOf(FileMetadata("b.txt", 1, "text/plain")),
                    ttl = 100, pasteLimit = null,
                )
            )
        }

        fx.time.advance(10)
        val removed = runBlocking { fx.service.deleteExpired() }

        removed shouldBe 1
        fx.repo.findBy(a.id) shouldBe null
        fx.repo.findBy(b.id).shouldNotBeNull()
        fx.storage.deletes[0] shouldContainExactly listOf("${a.id}/0-a.txt")
    }

    test("two bundles get different SSE keys") {
        val fx = Fixtures()
        val a = runBlocking {
            fx.service.copyFiles(CopyFilesRequest(listOf(FileMetadata("a", 1, "x")), 60, null))
        }
        val b = runBlocking {
            fx.service.copyFiles(CopyFilesRequest(listOf(FileMetadata("b", 1, "x")), 60, null))
        }

        val keyA = (fx.repo.findBy(a.id)!!.payload as Payload.Files).sseKey
        val keyB = (fx.repo.findBy(b.id)!!.payload as Payload.Files).sseKey

        keyA.size shouldBe 32
        keyB.size shouldBe 32
        keyA.contentEquals(keyB) shouldBe false
    }

    test("CopyFilesResponse never echoes the raw sseKey in any DTO field") {
        val fx = Fixtures()
        val response = runBlocking {
            fx.service.copyFiles(CopyFilesRequest(listOf(FileMetadata("a", 1, "x")), 60, null))
        }
        val sseKey = (fx.repo.findBy(response.id)!!.payload as Payload.Files).sseKey
        val base64 = java.util.Base64.getEncoder().encodeToString(sseKey)

        // The base64-encoded key SHOULD appear (it's the SSE-C header value).
        // We assert that the raw bytes are not surfaced anywhere else, e.g. inside the URL itself.
        response.uploads.forEach { upload ->
            upload.putUrl.contains(base64) shouldBe false
            upload.objectKey.contains(base64) shouldBe false
            // headers do contain the base64 key — that's the wire requirement
            upload.headers["x-amz-server-side-encryption-customer-key"] shouldBe base64
        }
    }
})
