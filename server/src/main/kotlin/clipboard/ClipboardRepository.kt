package com.github.spaceenthusiast.clipboard

interface ClipboardRepository {
    fun save(entity: ClipboardEntry)
    fun findBy(id: String): ClipboardEntry?
    fun delete(id: String)
}
