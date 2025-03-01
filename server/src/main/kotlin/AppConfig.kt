package com.github.spaceenthusiast

class AppConfig {

    var baseServerUrl: String
        private set

    var encryptionKey: String
        private set

    var useS3: Boolean
        private set

    var s3Config: S3Config
        private set

    init {
        baseServerUrl = System.getenv("BASE_SERVER_URL") ?: "http://127.0.0.1:8080"
        encryptionKey = System.getenv("ENCRYPTION_KEY") ?: "oyoushouldchange"

        useS3 = (System.getenv("USE_S3") ?: "false") == "true"
        s3Config = S3Config(
            endpoint =  System.getenv("S3_ENDPOINT") ?: "http://localhost:9000",
            accessKey = System.getenv("S3_ACCESS_KEY") ?: "access_key",
            secretKey = System.getenv("S3_SECRET_KEY") ?: "secret_key",
            bucketName = System.getenv("S3_BUCKET_NAME") ?: "default-bucket",
            region = System.getenv("S3_REGION") ?: "us-east-1",
        )
    }
}

data class S3Config(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucketName: String,
    val region: String
)