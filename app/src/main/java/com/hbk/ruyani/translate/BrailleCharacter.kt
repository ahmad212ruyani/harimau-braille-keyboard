package com.hbk.ruyani.translate

/**
 * Merepresentasikan satu sel karakter Braille (titik 1 sampai 8).
 * Menggunakan bitmask biner untuk efisiensi penyimpanan dan perbandingan cepat.
 *
 * Contoh 6 Titik:
 * Titik 1: bit 0 (1)      Titik 4: bit 3 (8)
 * Titik 2: bit 1 (2)      Titik 5: bit 4 (16)
 * Titik 3: bit 2 (4)      Titik 6: bit 5 (32)
 */
data class BrailleCharacter(val mask: Int) {

    constructor(vararg dots: Int) : this(
        dots.fold(0) { acc, dot ->
            if (dot in 1..8) acc or (1 shl (dot - 1)) else acc
        }
    )

    fun containsDot(dot: Int): Boolean {
        if (dot !in 1..8) return false
        return (mask and (1 shl (dot - 1))) != 0
    }

    val isEmpty: Boolean
        get() = mask == 0

    val dotNumbers: List<Int>
        get() = (1..8).filter { containsDot(it) }

    companion object {
        val EMPTY = BrailleCharacter(0)

        // Simbol kontrol standar
        val NUMBER_PREFIX = BrailleCharacter(3, 4, 5, 6)   // ⠼ (Awalan angka)
        val CAPITAL_PREFIX = BrailleCharacter(4, 6)        // ⠨ (Awalan huruf besar)
        val SPACE = BrailleCharacter(0)
    }
}
