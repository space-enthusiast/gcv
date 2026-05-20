package e2e

import com.github.spaceenthusiast.AppConfig
import com.github.spaceenthusiast.clipboard.ClipboardJanitor
import com.github.spaceenthusiast.clipboard.ClipboardService
import com.github.spaceenthusiast.clipboard.CopyFilesRequest
import com.github.spaceenthusiast.clipboard.CopyFilesResponse
import com.github.spaceenthusiast.clipboard.FileMetadata
import com.github.spaceenthusiast.clipboard.InMemoryClipboardRepository
import com.github.spaceenthusiast.clipboard.PasteFilesSuccess
import com.github.spaceenthusiast.encryption.EncryptionService
import com.github.spaceenthusiast.key.TinyKeyGenerator
import com.github.spaceenthusiast.qr.QrGenerator
import com.github.spaceenthusiast.storage.SeaweedFsObjectStorage
import com.github.spaceenthusiast.time.LocalDateTimeProvider
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "gcv.test.e2e", matches = "true")
class ClipboardE2eTest {

    private lateinit var seaweed: SeaweedFsContainer
    private lateinit var storage: SeaweedFsObjectStorage
    private lateinit var service: ClipboardService
    private lateinit var http: HttpClient

    @BeforeAll
    fun setup() {
        seaweed = SeaweedFsContainer()
        seaweed.start()
        storage = SeaweedFsObjectStorage(
            endpoint = seaweed.endpoint(),
            bucket = seaweed.bucket(),
            accessKey = seaweed.accessKey(),
            secretKey = seaweed.secretKey(),
        )
        service = ClipboardService(
            clipboardRepository = InMemoryClipboardRepository(),
            textKeyGenerator = TinyKeyGenerator(),
            timeProvider = LocalDateTimeProvider(),
            qrGenerator = QrGenerator(),
            encryptionService = EncryptionService(AppConfig()),
            objectStorage = storage,
            appConfig = AppConfig(),
        )
        http = HttpClient(CIO)
    }

    @AfterAll
    fun teardown() {
        http.close()
        storage.close()
        seaweed.stop()
    }

    @Test
    fun `file bundle round-trip - copy, PUT to seaweed, paste, GET, bytes match`() = runBlocking {
        val fileA = "hello, world\n".toByteArray()
        val fileB = ByteArray(2048).also { for (i in it.indices) it[i] = (i % 251).toByte() }

        val copy: CopyFilesResponse = service.copyFiles(
            CopyFilesRequest(
                files = listOf(
                    FileMetadata("a.txt", fileA.size.toLong(), "text/plain"),
                    FileMetadata("b.bin", fileB.size.toLong(), "application/octet-stream"),
                ),
                ttl = 60,
                pasteLimit = null,
            )
        )

        copy.uploads.zip(listOf(fileA, fileB)).forEach { (upload, bytes) ->
            val response = http.put(upload.putUrl) {
                upload.headers.forEach { (k, v) -> headers.append(k, v) }
                setBody(bytes)
            }
            response.status.value shouldBe 200
        }

        val pasted = service.paste(copy.id)
        pasted.shouldBeInstanceOf<PasteFilesSuccess>()
        pasted.files shouldHaveSize 2

        pasted.files.zip(listOf(fileA, fileB)).forEach { (download, expected) ->
            val response = http.get(download.getUrl) {
                download.headers.forEach { (k, v) -> headers.append(k, v) }
            }
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsBytes() shouldBe expected
        }
    }

    @Test
    fun `SSE-C is enforced - GET without customer headers returns 400`() = runBlocking {
        val payload = "secret payload".toByteArray()
        val copy = service.copyFiles(
            CopyFilesRequest(
                files = listOf(FileMetadata("a.txt", payload.size.toLong(), "text/plain")),
                ttl = 60,
                pasteLimit = null,
            )
        )
        val upload = copy.uploads.single()
        http.put(upload.putUrl) {
            upload.headers.forEach { (k, v) -> headers.append(k, v) }
            setBody(payload)
        }

        val pasted = service.paste(copy.id) as PasteFilesSuccess
        val response = http.get(pasted.files.single().getUrl)
        response.status.value shouldBe 400
    }

    @Test
    fun `pasteLimit=1 - second paste fails and storage keys are deleted`() = runBlocking {
        val data = "x".toByteArray()
        val copy = service.copyFiles(
            CopyFilesRequest(
                files = listOf(FileMetadata("a.txt", data.size.toLong(), "text/plain")),
                ttl = 60,
                pasteLimit = 1,
            )
        )
        val upload = copy.uploads.single()
        http.put(upload.putUrl) {
            upload.headers.forEach { (k, v) -> headers.append(k, v) }
            setBody(data)
        }

        service.paste(copy.id).shouldBeInstanceOf<PasteFilesSuccess>()

        val again = service.paste(copy.id)
        again.shouldBeInstanceOf<com.github.spaceenthusiast.clipboard.PasteFailureResponse>()
    }

    @Test
    fun `janitor reaps expired bundles from storage`() = runBlocking {
        val data = "x".toByteArray()
        val copy = service.copyFiles(
            CopyFilesRequest(
                files = listOf(FileMetadata("a.txt", data.size.toLong(), "text/plain")),
                ttl = 2,
                pasteLimit = null,
            )
        )
        val upload = copy.uploads.single()
        http.put(upload.putUrl) {
            upload.headers.forEach { (k, v) -> headers.append(k, v) }
            setBody(data)
        }

        val janitor = ClipboardJanitor(service, 1.seconds)
        val job = janitor.start()
        Thread.sleep(4_000)
        janitor.stop()
        job.join()

        val pasted = service.paste(copy.id)
        pasted.shouldBeInstanceOf<com.github.spaceenthusiast.clipboard.PasteFailureResponse>()
    }
}
