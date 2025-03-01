package com.github.spaceenthusiast.text

import com.github.spaceenthusiast.s3.S3Service
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.time.ZoneOffset

@Serializable
data class TextEntityJson(
    val id: String,
    val content: ByteArray,
    val ttl: Long,
    val expireAt: Long
) {
    companion object {
        fun fromJson(text: String): TextEntityJson {
            return Json.decodeFromString(text)
        }

        fun fromEntity(entity: TextEntity): TextEntityJson {
            return TextEntityJson(
                id = entity.id,
                content = entity.content,
                ttl = entity.ttl,
                expireAt = entity.expireAt.toInstant(ZoneOffset.UTC).toEpochMilli()
            )
        }
    }

    fun toJson() = Json.encodeToString(this)

    fun toEntity(): TextEntity {
        return TextEntity(
            id = id,
            content = content,
            ttl = ttl,
            expireAt = Instant.ofEpochMilli(expireAt)
                .atOffset(ZoneOffset.UTC)
                .toLocalDateTime()
        )
    }
}

class S3TextRepository(
    private val s3Service: S3Service
) : TextRepository {
    override fun save(entity: TextEntity) {
        s3Service.putObject(
            key = entity.id,
            content = TextEntityJson.fromEntity(entity).toJson()
        )
    }

    override fun findBy(id: String): TextEntity? {
        val response = s3Service.getObject(id) ?: return null
        return TextEntityJson.fromJson(response).toEntity()
    }
}