package io.github.forkyfor.oldphone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTest {
    @Test fun commandsInBothLanguages() {
        assertEquals(VoiceCommand.NEW_CHAT, VoiceCommands.match("New chat"))
        assertEquals(VoiceCommand.NEW_CHAT, VoiceCommands.match("nuova chat."))
        assertEquals(VoiceCommand.PROJECTS, VoiceCommands.match("Apri  progetti"))
        assertEquals(VoiceCommand.PROJECTS, VoiceCommands.match("open projects"))
        assertEquals(VoiceCommand.BACK, VoiceCommands.match("Indietro!"))
        assertEquals(VoiceCommand.BACK, VoiceCommands.match("go back"))
        assertEquals(VoiceCommand.STOP, VoiceCommands.match("ferma la lettura"))
        assertEquals(VoiceCommand.READ, VoiceCommands.match("leggi la risposta"))
    }

    @Test fun anythingLongerIsDictatedNotExecuted() {
        assertNull(VoiceCommands.match("nuova chat su come cucinare il riso"))
        assertNull(VoiceCommands.match("go back to the first idea and expand it"))
        assertNull(VoiceCommands.match("progetti"))
        assertNull(VoiceCommands.match(""))
    }

    @Test fun longTextIsSplitForTheSpeechEngine() {
        val text = (1..400).joinToString(" ") { "Sentence number $it." }
        val parts = Speaker.chunk(text, 500)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 500 })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
        assertEquals(listOf("short"), Speaker.chunk("short", 500))
    }

    @Test fun pinDelayGrowsAndIsCapped() {
        assertEquals(0L, Pin.delayFor(4))
        assertEquals(30_000L, Pin.delayFor(5))
        assertEquals(60_000L, Pin.delayFor(6))
        assertEquals(15 * 60_000L, Pin.delayFor(50))
    }
}
