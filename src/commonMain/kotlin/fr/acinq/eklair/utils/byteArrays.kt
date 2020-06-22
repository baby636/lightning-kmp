package fr.acinq.eklair.utils

import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor

fun ByteArray.leftPaddedCopyOf(n: Int): ByteArray {
    if (size >= n) return copyOf()

    val ret = ByteArray(n)
    val pad = n - size
    repeat (size) { ret[pad + it] = this[it] }

    return ret
}

private fun ByteArray.checkSizeEquals(other: ByteArray) {
    check(size == other.size) { "Byte arrays have different sizes (this: $size, other: ${other.size})" }
}

infix fun ByteArray.or(other: ByteArray): ByteArray {
    checkSizeEquals(other)
    return ByteArray(size) { this[it] or other[it] }
}

infix fun ByteArray.and(other: ByteArray): ByteArray {
    checkSizeEquals(other)
    return ByteArray(size) { this[it] and other[it] }
}

infix fun ByteArray.xor(other: ByteArray): ByteArray {
    checkSizeEquals(other)
    return ByteArray(size) { this[it] xor other[it] }
}