package io.github.forkyfor.oldphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCommandsTest {
    @Test fun callsByNameAndNumber() {
        assertEquals(PhoneCommand.Call("mario"), PhoneCommands.parse("Chiama Mario"))
        assertEquals(PhoneCommand.Call("mamma"), PhoneCommands.parse("chiama la mamma"))
        assertEquals(PhoneCommand.Call("mario rossi"), PhoneCommands.parse("Telefona a Mario Rossi."))
        assertEquals(PhoneCommand.Call("mom"), PhoneCommands.parse("call my mom"))
        assertEquals(PhoneCommand.Call("333 123 4567"), PhoneCommands.parse("chiama il 333 123 4567"))
        assertEquals("3331234567", PhoneCommands.dialable("333 123 4567"))
        assertEquals("+390212345", PhoneCommands.dialable("+39 02 12345"))
        assertNull(PhoneCommands.dialable("mario"))
    }

    @Test fun longSentencesGoToClaude() {
        assertNull(PhoneCommands.parse("chiama gli amici di mio fratello per organizzare la cena di sabato"))
        assertNull(PhoneCommands.parse("scrivimi una poesia sul mare"))
        assertNull(PhoneCommands.parse("apri una discussione sui pro e i contro del nucleare"))
    }

    @Test fun callTheNumberInTheReply() {
        assertEquals(PhoneCommand.CallFromReply, PhoneCommands.parse("Chiama il numero"))
        assertEquals(PhoneCommand.CallFromReply, PhoneCommands.parse("call that number"))
    }

    @Test fun messages() {
        assertEquals(
            PhoneCommand.Message("mario", "arrivo tra 10 minuti"),
            PhoneCommands.parse("manda un messaggio a Mario dicendo arrivo tra 10 minuti")
        )
        assertEquals(PhoneCommand.Message("anna", null), PhoneCommands.parse("send a message to Anna"))
        assertEquals(PhoneCommand.Message("luca", "i'm late"), PhoneCommands.parse("text Luca saying I'm late"))
    }

    @Test fun deviceCommands() {
        assertEquals(PhoneCommand.VolumeUp, PhoneCommands.parse("Alza il volume"))
        assertEquals(PhoneCommand.VolumeDown, PhoneCommands.parse("volume giù"))
        assertEquals(PhoneCommand.Mute, PhoneCommands.parse("silenzia"))
        assertEquals(PhoneCommand.Torch(true), PhoneCommands.parse("accendi la torcia"))
        assertEquals(PhoneCommand.Torch(false), PhoneCommands.parse("Turn off the flashlight"))
        assertEquals(PhoneCommand.Time, PhoneCommands.parse("Che ore sono?"))
        assertEquals(PhoneCommand.Battery, PhoneCommands.parse("quanta batteria"))
        assertEquals(PhoneCommand.Wifi, PhoneCommands.parse("apri il wifi"))
        assertEquals(PhoneCommand.Settings, PhoneCommands.parse("apri impostazioni"))
        assertEquals(PhoneCommand.OpenApp("calendario"), PhoneCommands.parse("apri calendario"))
        assertEquals(PhoneCommand.OpenApp("whatsapp"), PhoneCommands.parse("open the app WhatsApp"))
        assertEquals(PhoneCommand.Help, PhoneCommands.parse("comandi vocali"))
    }

    @Test fun answers() {
        assertEquals(Answer.YES, PhoneCommands.answer("Sì"))
        assertEquals(Answer.YES, PhoneCommands.answer("go ahead"))
        assertEquals(Answer.NO, PhoneCommands.answer("annulla"))
        assertNull(PhoneCommands.answer("forse più tardi"))
    }

    @Test fun phoneNumbersInText() {
        val reply = "La pizzeria Da Mario è aperta fino alle 23:00. Telefono: 02 1234 5678, oppure +39 333 765 4321. " +
            "Prenotazioni dal 12/05/2026, prezzo 1.250 euro."
        assertEquals(listOf("0212345678", "+393337654321"), PhoneCommands.findPhoneNumbers(reply))
        assertTrue(PhoneCommands.findPhoneNumbers("Nessun numero qui, solo l'anno 2026.").isEmpty())
    }

    @Test fun contactRanking() {
        val names = listOf("Mario Rossi", "Maria Bianchi", "Mamma", "Dott. Mario Verdi", "Nicolò D'Amico")
        val ranked = PhoneCommands.rank("mario rossi", names) { it }
        assertEquals("Mario Rossi", ranked.first().first)
        assertEquals(100, ranked.first().second)
        assertEquals("Mamma", PhoneCommands.rank("mamma", names) { it }.first().first)
        assertEquals("Nicolò D'Amico", PhoneCommands.rank("nicolo", names) { it }.first().first)
        // Two contacts called Mario: both match well, so the app must ask which one.
        val marios = PhoneCommands.rank("mario", names) { it }.filter { it.second >= PhoneCommands.SURE_MATCH }
        assertEquals(2, marios.size)
    }
}
