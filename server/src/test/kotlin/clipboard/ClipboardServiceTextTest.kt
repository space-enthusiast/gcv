package clipboard

import com.github.spaceenthusiast.clipboard.CopyTextRequest
import com.github.spaceenthusiast.clipboard.PasteFailureResponse
import com.github.spaceenthusiast.clipboard.PasteSuccessResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

class ClipboardServiceTextTest : FunSpec({

    test("copy then paste returns the same text") {
        val fx = Fixtures()
        val id = fx.service.copyText(CopyTextRequest(text = "hello", ttl = 60, pasteLimit = null)).id

        val response = runBlocking { fx.service.paste(id) }

        response.shouldBeInstanceOf<PasteSuccessResponse>()
        response.text shouldBe "hello"
    }

    test("paste after ttl returns failure and deletes the entry") {
        val fx = Fixtures()
        val id = fx.service.copyText(CopyTextRequest(text = "x", ttl = 10, pasteLimit = null)).id

        fx.time.advance(11)
        val response = runBlocking { fx.service.paste(id) }

        response.shouldBeInstanceOf<PasteFailureResponse>()
        fx.repo.findBy(id) shouldBe null
    }

    test("pasteLimit=1 consumes the entry after one paste") {
        val fx = Fixtures()
        val id = fx.service.copyText(CopyTextRequest(text = "x", ttl = 60, pasteLimit = 1)).id

        runBlocking { fx.service.paste(id) }.shouldBeInstanceOf<PasteSuccessResponse>()
        fx.repo.findBy(id) shouldBe null

        runBlocking { fx.service.paste(id) }.shouldBeInstanceOf<PasteFailureResponse>()
    }

    test("pasteLimit=2 decrements and keeps the entry on first paste") {
        val fx = Fixtures()
        val id = fx.service.copyText(CopyTextRequest(text = "x", ttl = 60, pasteLimit = 2)).id

        runBlocking { fx.service.paste(id) }.shouldBeInstanceOf<PasteSuccessResponse>()
        fx.repo.findBy(id)?.remainingPastes shouldBe 1

        runBlocking { fx.service.paste(id) }.shouldBeInstanceOf<PasteSuccessResponse>()
        fx.repo.findBy(id) shouldBe null
    }

    test("pasteLimit=0 is rejected at copy time") {
        val fx = Fixtures()
        try {
            fx.service.copyText(CopyTextRequest(text = "x", ttl = 60, pasteLimit = 0))
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            e.message shouldBe "pasteLimit must be > 0 when provided"
        }
    }
})
