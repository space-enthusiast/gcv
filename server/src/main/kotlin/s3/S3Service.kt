package com.github.spaceenthusiast.s3

import com.github.spaceenthusiast.S3Config
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI

class S3Service(private val s3Config: S3Config) {

    private val s3Client: S3Client
    private val logger = LoggerFactory.getLogger(S3Service::class.java)

    init {
        val credentials = AwsBasicCredentials.create(
            s3Config.accessKey,
            s3Config.secretKey,
        )
        s3Client = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .endpointOverride(URI(s3Config.endpoint))
            .region(Region.of(s3Config.region))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .build()
    }

    fun putObject(key: String, content: String) {
        val request = PutObjectRequest.builder()
            .bucket(s3Config.bucketName)
            .key(key)
            .build()

        val body = RequestBody.fromString(content)

        val response = s3Client.putObject(request, body)

        logger.info(response.toString())
    }

    fun getObject(key: String): String? {
        val request = GetObjectRequest.builder()
            .bucket(s3Config.bucketName)
            .key(key)
            .build()

        try {
            val response = s3Client.getObjectAsBytes(request)
            logger.info(response.toString())
            return response.asUtf8String()
        } catch (exception: NoSuchKeyException) {
            return null
        }
    }
}