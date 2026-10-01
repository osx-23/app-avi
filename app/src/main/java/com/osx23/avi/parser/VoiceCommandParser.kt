package com.osx23.avi.parser

import java.text.Normalizer

data class ParsedVoiceCommand(
    val tipo: String? = null,
    val via: Int? = null,
    val placa: String? = null
)

object VoiceCommandParser {
    private val phonetic = mapOf(
        "alfa" to "A", "alpha" to "A", "bravo" to "B", "charlie" to "C",
        "delta" to "D", "echo" to "E", "foxtrot" to "F", "golf" to "G",
        "hotel" to "H", "india" to "I", "juliet" to "J", "juliett" to "J",
        "kilo" to "K", "lima" to "L", "mike" to "M", "november" to "N",
        "oscar" to "O", "papa" to "P", "quebec" to "Q", "romeo" to "R",
        "sierra" to "S", "tango" to "T", "uniform" to "U", "victor" to "V",
        "whiskey" to "W", "whisky" to "W", "xray" to "X", "yankee" to "Y",
        "zulu" to "Z"
    )

    private val digitWords = mapOf(
        "cero" to "0", "uno" to "1", "una" to "1", "dos" to "2",
        "tres" to "3", "cuatro" to "4", "cinco" to "5", "seis" to "6",
        "siete" to "7", "ocho" to "8", "nueve" to "9"
    )

    fun parse(raw: String): ParsedVoiceCommand {
        val text = normalize(raw).replace("x ray", "xray").replace("x-ray", "xray")
        val tipo = if (text.contains("fuga")) "FUGA" else null
        val viaSegment = if (text.contains("via")) {
            text.substringAfter("via").substringBefore("placa").trim()
        } else ""
        val plateSegment = if (text.contains("placa")) text.substringAfter("placa").trim() else ""

        return ParsedVoiceCommand(
            tipo = tipo,
            via = parseSpanishNumber(viaSegment),
            placa = parsePlate(plateSegment)
        )
    }

    internal fun parsePlate(segment: String): String? {
        if (segment.isBlank()) return null
        val value = segment.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString("") { token ->
                when {
                    phonetic.containsKey(token) -> phonetic.getValue(token)
                    digitWords.containsKey(token) -> digitWords.getValue(token)
                    token.all { it.isDigit() } -> token
                    token.length == 1 && token[0] in 'a'..'z' -> token.uppercase()
                    else -> ""
                }
            }
            .filter { it.isLetterOrDigit() }
            .uppercase()
        return value.ifBlank { null }
    }

    internal fun parseSpanishNumber(segment: String): Int? {
        if (segment.isBlank()) return null
        Regex("\\d+").find(segment)?.value?.toIntOrNull()?.let { return it }

        val joined = segment.trim()
        val special = mapOf(
            "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14,
            "quince" to 15, "dieciseis" to 16, "diecisiete" to 17,
            "dieciocho" to 18, "diecinueve" to 19, "veinte" to 20,
            "veintiuno" to 21, "veintidos" to 22, "veintitres" to 23,
            "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
            "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29
        )
        special[joined]?.let { return it }

        val units = mapOf(
            "cero" to 0, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3,
            "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7,
            "ocho" to 8, "nueve" to 9, "diez" to 10
        )
        val tens = mapOf(
            "veinte" to 20, "treinta" to 30, "cuarenta" to 40,
            "cincuenta" to 50, "sesenta" to 60, "setenta" to 70,
            "ochenta" to 80, "noventa" to 90
        )
        val hundreds = mapOf(
            "cien" to 100, "ciento" to 100, "doscientos" to 200,
            "trescientos" to 300, "cuatrocientos" to 400, "quinientos" to 500,
            "seiscientos" to 600, "setecientos" to 700, "ochocientos" to 800,
            "novecientos" to 900
        )

        var total = 0
        var recognized = false
        for (token in joined.split(Regex("\\s+"))) {
            when {
                token == "y" -> Unit
                special.containsKey(token) -> { total += special.getValue(token); recognized = true }
                hundreds.containsKey(token) -> { total += hundreds.getValue(token); recognized = true }
                tens.containsKey(token) -> { total += tens.getValue(token); recognized = true }
                units.containsKey(token) -> { total += units.getValue(token); recognized = true }
            }
        }
        return if (recognized) total else null
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
