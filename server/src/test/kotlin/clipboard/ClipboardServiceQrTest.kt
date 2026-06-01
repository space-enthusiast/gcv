package clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ClipboardServiceQrTest : FunSpec({

    test("getQrImage returns a valid PNG regardless of whether the id exists") {
        val fx = Fixtures()

        val bytes = fx.service.getQrImage("does-not-exist")

        // PNG magic header: 89 50 4E 47 0D 0A 1A 0A
        bytes.size shouldBe bytes.size.coerceAtLeast(8)
        bytes[0] shouldBe 0x89.toByte()
        bytes[1] shouldBe 0x50.toByte()
        bytes[2] shouldBe 0x4E.toByte()
        bytes[3] shouldBe 0x47.toByte()
    }
})
