package com.osx23.avi.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {
    @Test fun parsesDirectVia() {
        val result = VoiceCommandParser.parse("Fuga vía 151")
        assertEquals("FUGA", result.tipo)
        assertEquals(151, result.via)
    }

    @Test fun parsesSpokenVia() {
        assertEquals(151, VoiceCommandParser.parse("Fuga vía ciento cincuenta y uno").via)
    }

    @Test fun parsesPhoneticPlate() {
        assertEquals(
            "BTL245",
            VoiceCommandParser.parse("Placa Bravo Tango Lima dos cuatro cinco").placa
        )
    }

    @Test fun parsesCompleteCommand() {
        val result = VoiceCommandParser.parse(
            "Fuga vía ciento cincuenta y uno placa Bravo Tango Lima dos cuatro cinco"
        )
        assertEquals("FUGA", result.tipo)
        assertEquals(151, result.via)
        assertEquals("BTL245", result.placa)
    }
}
