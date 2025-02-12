package com.github.spaceenthusiast.key

import java.security.SecureRandom

class TinyKeyGenerator : TextKeyGenerator {

    private val random : SecureRandom by lazy { SecureRandom() }
    private val characters = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val keyLength = 7

    override fun generate(): String {
        return (1..keyLength)
            .map { characters[random.nextInt(characters.length)] }
            .joinToString("")
    }
}