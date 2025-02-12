package com.github.spaceenthusiast.key

import java.util.*

class UuidKeyGenerator : TextKeyGenerator {
    override fun generate(): String {
        return UUID.randomUUID().toString().replace("-", "") // TODO Change ID generation algorithm
    }
}