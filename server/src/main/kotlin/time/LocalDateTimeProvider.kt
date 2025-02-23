package com.github.spaceenthusiast.time

import java.time.LocalDateTime

class LocalDateTimeProvider : TimeProvider {
    override fun now(): LocalDateTime {
        return LocalDateTime.now()
    }
}