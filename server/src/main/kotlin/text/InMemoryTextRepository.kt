package com.github.spaceenthusiast.text

import java.util.concurrent.ConcurrentHashMap

class InMemoryTextRepository : TextRepository {
    private val map = ConcurrentHashMap<String, TextEntity>()

    override fun save(entity: TextEntity) {
        map[entity.id] = entity
    }

    override fun findBy(id: String): TextEntity? {
        return map[id]
    }
}