package com.github.spaceenthusiast.storage

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.DeleteObjectsRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import aws.smithy.kotlin.runtime.net.url.Url
import java.security.MessageDigest
import java.util.Base64
import kotlin.time.Duration

class SeaweedFsObjectStorage(
    private val endpoint: String,
    private val bucket: String,
    accessKey: String,
    secretKey: String,
    private val region: String = "us-east-1",
) : ObjectStorage, AutoCloseable {

    private val client: S3Client = S3Client {
        region = this@SeaweedFsObjectStorage.region
        endpointUrl = Url.parse(endpoint)
        forcePathStyle = true
        credentialsProvider = StaticCredentialsProvider {
            accessKeyId = accessKey
            secretAccessKey = secretKey
        }
    }

    override suspend fun presignPut(
        key: String,
        contentType: String,
        sseKey: ByteArray,
        ttl: Duration,
    ): PresignedRequest {
        require(sseKey.size == 32) { "sseKey must be 32 bytes (AES-256)" }

        val (keyB64, md5B64) = encodeSseHeaders(sseKey)

        val request = PutObjectRequest {
            this.bucket = this@SeaweedFsObjectStorage.bucket
            this.key = key
            this.contentType = contentType
            sseCustomerAlgorithm = "AES256"
            sseCustomerKey = keyB64
            sseCustomerKeyMd5 = md5B64
        }

        val signed = client.presignPutObject(request, ttl)
        return PresignedRequest(
            url = signed.url.toString(),
            headers = sseHeaders(keyB64, md5B64),
        )
    }

    override suspend fun presignGet(
        key: String,
        sseKey: ByteArray,
        ttl: Duration,
    ): PresignedRequest {
        require(sseKey.size == 32) { "sseKey must be 32 bytes (AES-256)" }

        val (keyB64, md5B64) = encodeSseHeaders(sseKey)

        val request = GetObjectRequest {
            this.bucket = this@SeaweedFsObjectStorage.bucket
            this.key = key
            sseCustomerAlgorithm = "AES256"
            sseCustomerKey = keyB64
            sseCustomerKeyMd5 = md5B64
        }

        val signed = client.presignGetObject(request, ttl)
        return PresignedRequest(
            url = signed.url.toString(),
            headers = sseHeaders(keyB64, md5B64),
        )
    }

    override suspend fun delete(keys: List<String>) {
        if (keys.isEmpty()) return
        client.deleteObjects(
            DeleteObjectsRequest {
                bucket = this@SeaweedFsObjectStorage.bucket
                delete = Delete {
                    objects = keys.map { k -> ObjectIdentifier { key = k } }
                }
            }
        )
    }

    override fun close() {
        client.close()
    }

    private fun encodeSseHeaders(sseKey: ByteArray): Pair<String, String> {
        val keyB64 = Base64.getEncoder().encodeToString(sseKey)
        val md5 = MessageDigest.getInstance("MD5").digest(sseKey)
        val md5B64 = Base64.getEncoder().encodeToString(md5)
        return keyB64 to md5B64
    }

    private fun sseHeaders(keyB64: String, md5B64: String): Map<String, String> = mapOf(
        "x-amz-server-side-encryption-customer-algorithm" to "AES256",
        "x-amz-server-side-encryption-customer-key" to keyB64,
        "x-amz-server-side-encryption-customer-key-MD5" to md5B64,
    )
}
