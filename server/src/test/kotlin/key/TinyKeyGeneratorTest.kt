package key

import com.github.spaceenthusiast.key.TinyKeyGenerator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TinyKeyGeneratorTest : FunSpec({
    val tinyKeyGenerator = TinyKeyGenerator()

    test("key length should be 7") {
        val key = tinyKeyGenerator.generate()

        key.length shouldBe 7
    }
})