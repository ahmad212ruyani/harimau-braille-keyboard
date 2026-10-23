package com.hbk.ruyani.translate

/**
 * Mesin penerjemah titik Braille ke teks (Bahasa Indonesia & Universal UEB).
 * Mendukung huruf a-z, mode angka, huruf kapital, tanda baca, serta modular
 * untuk penambahan tabel singkatan Grade 2 di masa depan.
 */
class BrailleTranslator {

    private var isCapitalNext = false
    private var isCapsLock = false
    private var isNumberMode = false

    // Pemetaan Standar Braille Grade 1: Sel -> Karakter Huruf
    private val letterMap = mapOf(
        BrailleCharacter(1) to 'a',
        BrailleCharacter(1, 2) to 'b',
        BrailleCharacter(1, 4) to 'c',
        BrailleCharacter(1, 4, 5) to 'd',
        BrailleCharacter(1, 5) to 'e',
        BrailleCharacter(1, 2, 4) to 'f',
        BrailleCharacter(1, 2, 4, 5) to 'g',
        BrailleCharacter(1, 2, 5) to 'h',
        BrailleCharacter(2, 4) to 'i',
        BrailleCharacter(2, 4, 5) to 'j',
        BrailleCharacter(1, 3) to 'k',
        BrailleCharacter(1, 2, 3) to 'l',
        BrailleCharacter(1, 3, 4) to 'm',
        BrailleCharacter(1, 3, 4, 5) to 'n',
        BrailleCharacter(1, 3, 5) to 'o',
        BrailleCharacter(1, 2, 3, 4) to 'p',
        BrailleCharacter(1, 2, 3, 4, 5) to 'q',
        BrailleCharacter(1, 2, 3, 5) to 'r',
        BrailleCharacter(2, 3, 4) to 's',
        BrailleCharacter(2, 3, 4, 5) to 't',
        BrailleCharacter(1, 3, 6) to 'u',
        BrailleCharacter(1, 2, 3, 6) to 'v',
        BrailleCharacter(2, 4, 5, 6) to 'w',
        BrailleCharacter(1, 3, 4, 6) to 'x',
        BrailleCharacter(1, 3, 4, 5, 6) to 'y',
        BrailleCharacter(1, 3, 5, 6) to 'z'
    )

    // Pemetaan Angka saat mode angka aktif (menggunakan pola a-j)
    private val numberMap = mapOf(
        BrailleCharacter(1) to '1',
        BrailleCharacter(1, 2) to '2',
        BrailleCharacter(1, 4) to '3',
        BrailleCharacter(1, 4, 5) to '4',
        BrailleCharacter(1, 5) to '5',
        BrailleCharacter(1, 2, 4) to '6',
        BrailleCharacter(1, 2, 4, 5) to '7',
        BrailleCharacter(1, 2, 5) to '8',
        BrailleCharacter(2, 4) to '9',
        BrailleCharacter(2, 4, 5) to '0'
    )

    // Pemetaan Tanda Baca
    private val punctuationMap = mapOf(
        BrailleCharacter(2) to ',',
        BrailleCharacter(2, 3) to ';',
        BrailleCharacter(2, 5) to ':',
        BrailleCharacter(2, 5, 6) to '.',
        BrailleCharacter(2, 3, 5) to '!',
        BrailleCharacter(2, 3, 6) to '?',
        BrailleCharacter(3) to '\'',
        BrailleCharacter(3, 6) to '-',
        BrailleCharacter(3, 4) to '/',
        BrailleCharacter(2, 3, 5, 6) to '(',
        BrailleCharacter(4, 1) to '@',
        BrailleCharacter(3, 4, 5, 6) to '#' // jika tidak diikuti angka
    )

    // Kamus Singkatan Grade 2 (dapat diperluas)
    private val grade2Map = mapOf(
        BrailleCharacter(1, 2, 3, 4, 6) to "dan", // and / dan
        BrailleCharacter(1, 2, 3, 4, 5, 6) to "untuk", // for / untuk
        BrailleCharacter(1, 3, 4, 5, 6) to "yang"
    )

    sealed class TranslationResult {
        data class CharacterOutput(val text: String, val spokenDescription: String = text) : TranslationResult()
        data class ControlAction(val type: ControlType, val description: String) : TranslationResult()
        object Unrecognized : TranslationResult()
    }

    enum class ControlType {
        CAPITAL_ONCE,
        CAPS_LOCK_TOGGLED,
        NUMBER_MODE_ON,
        MODE_RESET
    }

    /**
     * Menerjemahkan sel Braille menjadi karakter atau aksi kontrol.
     */
    fun translate(character: BrailleCharacter): TranslationResult {
        // Cek kontrol awalan Angka (titik 3, 4, 5, 6)
        if (character == BrailleCharacter.NUMBER_PREFIX) {
            isNumberMode = true
            return TranslationResult.ControlAction(ControlType.NUMBER_MODE_ON, "Mode angka")
        }

        // Cek kontrol huruf kapital (titik 4, 6)
        if (character == BrailleCharacter.CAPITAL_PREFIX) {
            return if (isCapitalNext) {
                // Tekan dua kali berturut-turut -> Caps Lock
                isCapsLock = !isCapsLock
                isCapitalNext = false
                TranslationResult.ControlAction(
                    ControlType.CAPS_LOCK_TOGGLED,
                    if (isCapsLock) "Caps Lock aktif" else "Caps Lock mati"
                )
            } else {
                isCapitalNext = true
                TranslationResult.ControlAction(ControlType.CAPITAL_ONCE, "Huruf besar")
            }
        }

        // 1. Cek mode angka
        if (isNumberMode) {
            val num = numberMap[character]
            if (num != null) {
                return TranslationResult.CharacterOutput(num.toString(), num.toString())
            }
        }

        // 2. Cek huruf a-z
        val letter = letterMap[character]
        if (letter != null) {
            isNumberMode = false // Huruf mematikan mode angka
            val shouldCapitalize = isCapsLock xor isCapitalNext
            val resultChar = if (shouldCapitalize) letter.uppercaseChar() else letter
            isCapitalNext = false
            return TranslationResult.CharacterOutput(
                resultChar.toString(),
                if (shouldCapitalize) "Kapital $resultChar" else resultChar.toString()
            )
        }

        // 3. Cek tanda baca
        val punct = punctuationMap[character]
        if (punct != null) {
            return TranslationResult.CharacterOutput(punct.toString(), getPunctuationName(punct))
        }

        // 4. Cek Grade 2
        val contracted = grade2Map[character]
        if (contracted != null) {
            isNumberMode = false
            val finalText = if (isCapitalNext || isCapsLock) {
                contracted.replaceFirstChar { it.uppercase() }
            } else {
                contracted
            }
            isCapitalNext = false
            return TranslationResult.CharacterOutput(finalText, finalText)
        }

        return TranslationResult.Unrecognized
    }

    fun onSpaceEntered() {
        // Spasi mereset mode angka dan kapital satu huruf
        isNumberMode = false
        isCapitalNext = false
    }

    fun resetState() {
        isCapitalNext = false
        isCapsLock = false
        isNumberMode = false
    }

    private fun getPunctuationName(ch: Char): String = when (ch) {
        ',' -> "Koma"
        '.' -> "Titik"
        '!' -> "Tanda seru"
        '?' -> "Tanda tanya"
        ':' -> "Titik dua"
        ';' -> "Titik koma"
        '-' -> "Tanda hubung"
        '\'' -> "Petik satu"
        '/' -> "Garis miring"
        '(' -> "Buka kurung"
        '@' -> "At"
        '#' -> "Pagar"
        else -> ch.toString()
    }
}
