package encryption

import com.github.spaceenthusiast.AppConfig
import com.github.spaceenthusiast.encryption.EncryptionService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EncryptionServiceTest : FunSpec({
    val appConfig = AppConfig()
    val encryptionService = EncryptionService(appConfig)

    test("encrypt and decrypt") {
        val plainText = "data"
        val cipherBytes = encryptionService.encrypt(plainText)
        val decryptedText = encryptionService.decrypt(cipherBytes)

        plainText shouldBe decryptedText
    }
})