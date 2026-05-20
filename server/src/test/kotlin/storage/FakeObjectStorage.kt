package storage

import com.github.spaceenthusiast.storage.ObjectStorage
import com.github.spaceenthusiast.storage.PresignedRequest
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

class FakeObjectStorage : ObjectStorage {

    data class PutCall(val key: String, val contentType: String, val sseKey: ByteArray, val ttl: Duration)
    data class GetCall(val key: String, val sseKey: ByteArray, val ttl: Duration)

    val puts = mutableListOf<PutCall>()
    val gets = mutableListOf<GetCall>()
    val deletes = mutableListOf<List<String>>()
    private val counter = AtomicInteger(0)

    override suspend fun presignPut(
        key: String,
        contentType: String,
        sseKey: ByteArray,
        ttl: Duration,
    ): PresignedRequest {
        puts += PutCall(key, contentType, sseKey.copyOf(), ttl)
        val n = counter.incrementAndGet()
        return PresignedRequest(
            url = "http://fake/$key?token=$n",
            headers = sseHeaders(sseKey),
        )
    }

    override suspend fun presignGet(
        key: String,
        sseKey: ByteArray,
        ttl: Duration,
    ): PresignedRequest {
        gets += GetCall(key, sseKey.copyOf(), ttl)
        val n = counter.incrementAndGet()
        return PresignedRequest(
            url = "http://fake/$key?token=$n",
            headers = sseHeaders(sseKey),
        )
    }

    override suspend fun delete(keys: List<String>) {
        deletes += keys
    }

    private fun sseHeaders(sseKey: ByteArray): Map<String, String> {
        val keyB64 = Base64.getEncoder().encodeToString(sseKey)
        val md5 = java.security.MessageDigest.getInstance("MD5").digest(sseKey)
        val md5B64 = Base64.getEncoder().encodeToString(md5)
        return mapOf(
            "x-amz-server-side-encryption-customer-algorithm" to "AES256",
            "x-amz-server-side-encryption-customer-key" to keyB64,
            "x-amz-server-side-encryption-customer-key-MD5" to md5B64,
        )
    }
}
