package com.github.spaceenthusiast.time

import java.time.LocalDateTime

interface TimeProvider {
    fun now(): LocalDateTime
}