package com.github.spaceenthusiast.clipboard

import java.util.concurrent.ConcurrentHashMap

class InMemoryClipboardRepository : ClipboardRepository {
    private val map = ConcurrentHashMap<String, ClipboardEntry>()

    override fun save(entity: ClipboardEntry) {
        map[entity.id] = entity
    }

    override fun findBy(id: String): ClipboardEntry? {
        return map[id]
    }

    override fun findAll(): List<ClipboardEntry> {
        return map.values.toList()
    }

    override fun delete(id: String) {
        map.remove(id)
    }
}
