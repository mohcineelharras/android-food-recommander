package com.foodrecommender.core

import java.io.ByteArrayOutputStream
import java.io.InputStream

const val MAX_PLACES_BODY_CHARS = 1_000_000

fun readLimitedUtf8(input: InputStream, maxBytes: Int): String {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw IllegalArgumentException(UserMessages.RESPONSE_TOO_LARGE)
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8)
}
