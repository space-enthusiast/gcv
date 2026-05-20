package com.github.spaceenthusiast.clipboard

interface ClipboardRepository {
    fun save(entity: ClipboardEntry)
    fun findBy(id: String): ClipboardEntry?
    fun findAll(): List<ClipboardEntry>
    fun delete(id: String)
}
