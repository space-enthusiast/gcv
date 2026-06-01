package clipboard

import com.github.spaceenthusiast.AppConfig
import com.github.spaceenthusiast.clipboard.ClipboardService
import com.github.spaceenthusiast.clipboard.InMemoryClipboardRepository
import com.github.spaceenthusiast.encryption.EncryptionService
import com.github.spaceenthusiast.key.TextKeyGenerator
import com.github.spaceenthusiast.qr.QrGenerator
import com.github.spaceenthusiast.time.TimeProvider
import storage.FakeObjectStorage
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

class FakeTimeProvider(initial: LocalDateTime = LocalDateTime.of(2026, 5, 20, 12, 0, 0)) : TimeProvider {
    private var current: LocalDateTime = initial
    override fun now(): LocalDateTime = current
    fun advance(seconds: Long) { current = current.plusSeconds(seconds) }
    fun set(t: LocalDateTime) { current = t }
}

class SequentialKeyGenerator(private val prefix: String = "id") : TextKeyGenerator {
    private val counter = AtomicInteger(0)
    override fun generate(): String = "$prefix${counter.incrementAndGet()}"
}

class Fixtures(
    val appConfig: AppConfig = AppConfig(),
    val repo: InMemoryClipboardRepository = InMemoryClipboardRepository(),
    val keyGen: SequentialKeyGenerator = SequentialKeyGenerator(),
    val time: FakeTimeProvider = FakeTimeProvider(),
    val storage: FakeObjectStorage = FakeObjectStorage(),
    val random: SecureRandom = SecureRandom().apply { setSeed(42L) },
) {
    val service: ClipboardService = ClipboardService(
        clipboardRepository = repo,
        textKeyGenerator = keyGen,
        timeProvider = time,
        qrGenerator = QrGenerator(),
        encryptionService = EncryptionService(appConfig),
        objectStorage = storage,
        appConfig = appConfig,
        secureRandom = random,
    )
}
