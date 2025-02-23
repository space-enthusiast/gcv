package com.github.spaceenthusiast.text

interface TextRepository {
    fun save(entity: TextEntity)
    fun findBy(id: String): TextEntity?
}