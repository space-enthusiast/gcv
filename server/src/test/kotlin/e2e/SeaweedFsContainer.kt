package e2e

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.createBucket
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

class SeaweedFsContainer(
    private val bucket: String = "gcv",
    private val accessKey: String = "any",
    private val secretKey: String = "any",
) {
    private val container: GenericContainer<*> = GenericContainer(
        DockerImageName.parse("chrislusf/seaweedfs:latest")
    )
        .withExposedPorts(S3_PORT, MASTER_PORT)
        .withCommand("server -s3 -dir=/data")
        .waitingFor(Wait.forHttp("/cluster/healthz").forPort(MASTER_PORT).forStatusCode(200))

    fun start() {
        if (container.isRunning) return
        container.start()
        runBlocking { createBucket() }
    }

    fun stop() {
        container.stop()
    }

    fun endpoint(): String = "http://${container.host}:${container.getMappedPort(S3_PORT)}"
    fun bucket(): String = bucket
    fun accessKey(): String = accessKey
    fun secretKey(): String = secretKey

    private suspend fun createBucket() {
        S3Client {
            region = "us-east-1"
            endpointUrl = Url.parse(endpoint())
            forcePathStyle = true
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = accessKey
                secretAccessKey = secretKey
            }
        }.use { client ->
            try {
                client.createBucket { this.bucket = this@SeaweedFsContainer.bucket }
            } catch (_: Exception) {
                // already exists — fine
            }
        }
    }

    companion object {
        private const val S3_PORT = 8333
        private const val MASTER_PORT = 9333
    }
}
